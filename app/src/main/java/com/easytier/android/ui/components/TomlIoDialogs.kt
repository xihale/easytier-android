package com.easytier.android.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.android.core.toml.TomlImporter
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.ui.icons.AppIcons

private val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/**
 * TOML 导入对话框。
 *
 * 多行等宽输入框支持直接粘贴；也可经系统文件选择器读取 .toml。
 * 解析失败时对话框保持打开，错误内联展示（tomlj 报错可能多行）。
 */
@Composable
fun TomlImportDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onImported: (NetworkConfig) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var error by remember { mutableStateOf<String?>(null) }

    fun tryParse(raw: String) {
        TomlImporter.parse(raw)
            .onSuccess { cfg ->
                error = null
                onImported(cfg)
            }
            .onFailure { e -> error = e.message ?: "解析失败" }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.fold(
            onSuccess = { content ->
                if (content.isNullOrBlank()) {
                    error = "所选文件为空或无法读取"
                } else {
                    text = content
                    error = null
                }
            },
            onFailure = { e -> error = "读取文件失败：${e.message}" },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 TOML 配置") },
        text = {
            Column {
                Text(
                    "粘贴 EasyTier TOML 文本，或从文件导入。导入会覆盖当前所有设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    placeholder = { Text("# instance_name = ...\n[network_identity]\nnetwork_name = \"default\"") },
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                )
                error?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .heightIn(max = 120.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
                TextButton(onClick = { pickFile.launch("*/*") }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(AppIcons.FolderOpen, null, Modifier.size(18.dp))
                    Text("从文件选择", Modifier.padding(start = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { tryParse(text.trim()) },
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * TOML 导出对话框：只读预览当前配置生成的 TOML，可复制 / 分享文本 / 存为文件（SAF）。
 */
@Composable
fun TomlExportDialog(
    toml: String,
    networkName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(toml.toByteArray())
            } ?: throw IllegalStateException("无法写入所选位置")
        }.fold(
            onSuccess = { toast("已保存 $networkName.toml") },
            onFailure = { e -> toast("保存失败：${e.message}") },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 TOML 配置") },
        text = {
            Column {
                Text(
                    "「${networkName}」的完整配置，可导入到其他 EasyTier 客户端。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            toml,
                            style = MonoStyle.copy(fontSize = 12.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
                // 提示导出内容不含本机身份，导入方会自动生成自己的
                Text(
                    "不含本机 instance_id，导入方会自动生成自己的身份",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(toml))
                        toast("已复制到剪贴板")
                    }) {
                        Icon(AppIcons.ContentCopy, null, Modifier.size(18.dp))
                        Text("复制", Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "$networkName.toml")
                            putExtra(Intent.EXTRA_TEXT, toml)
                        }
                        context.startActivity(Intent.createChooser(send, "分享 TOML 配置"))
                    }) {
                        Icon(AppIcons.Share, null, Modifier.size(18.dp))
                        Text("分享", Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { saveFile.launch(suggestedFileName(networkName)) }) {
                        Icon(AppIcons.Download, null, Modifier.size(18.dp))
                        Text("存为文件", Modifier.padding(start = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

/** 网络名转安全文件名。 */
private fun suggestedFileName(networkName: String): String =
    networkName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_', '.')
        .ifBlank { "easytier" } + ".toml"
