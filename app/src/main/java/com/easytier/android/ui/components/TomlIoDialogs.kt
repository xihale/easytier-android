package com.easytier.android.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easytier.android.core.toml.TomlImporter
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.ui.icons.AppIcons
import kotlinx.coroutines.delay

private val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

// 代码面板固定深色（终端隐喻，浅深主题同一套），语法色取自应用深色色板
private val CodeBg = Color(0xFF171A22)
private val CodeFg = Color(0xFFC4C8D9)
private val CodeMuted = Color(0xFF8D92A8)
private val CodeLine = Color(0xFF3E4356)
private val CodeIndigo = Color(0xFFB7C3FF)
private val CodeEmerald = Color(0xFF80D9C7)
private val CodeAmber = Color(0xFFFBBF24)
private val CodeErrorBg = Color(0xFF3D1A20)
private val CodeErrorFg = Color(0xFFFFB4AB)

/**
 * TOML 语法高亮（够用版）：注释灰斜体、行首表头靛蓝、字符串青绿、数值/日期琥珀、
 * 布尔靛蓝加粗；键与标点走默认前景色。字符串先于注释消费，避免 # 被串内误判。
 */
internal fun highlightToml(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder(text.length)
    builder.append(text)
    val n = text.length
    var i = 0

    fun lineEnd(from: Int): Int {
        var j = from
        while (j < n && text[j] != '\n') j++
        return j
    }

    fun isLineStart(idx: Int): Boolean {
        var j = idx - 1
        while (j >= 0 && text[j] != '\n') {
            if (text[j] != ' ' && text[j] != '\t') return false
            j--
        }
        return true
    }

    while (i < n) {
        val ch = text[i]
        when {
            ch == '#' -> {
                val end = lineEnd(i)
                builder.addStyle(SpanStyle(color = CodeMuted, fontStyle = FontStyle.Italic), i, end)
                i = end
            }
            ch == '"' || ch == '\'' -> {
                val quote = ch
                val triple = text.startsWith("$quote$quote$quote", i)
                var j = i + if (triple) 3 else 1
                var end = -1
                while (j < n) {
                    if (quote == '"' && text[j] == '\\') {
                        j += 2
                        continue
                    }
                    if (text[j] == quote) {
                        if (!triple) {
                            end = j + 1
                            break
                        }
                        if (text.startsWith("$quote$quote$quote", j)) {
                            end = j + 3
                            break
                        }
                    }
                    if (!triple && text[j] == '\n') break
                    j++
                }
                if (end == -1) end = if (triple) n else lineEnd(i)
                builder.addStyle(SpanStyle(color = CodeEmerald), i, end)
                i = end
            }
            ch == '[' && isLineStart(i) -> {
                val close = text.indexOf(']', i)
                val end = (if (close == -1) lineEnd(i) else close + 1).coerceAtMost(n)
                builder.addStyle(SpanStyle(color = CodeIndigo, fontWeight = FontWeight.SemiBold), i, end)
                i = end
            }
            ch.isLetterOrDigit() || ch in "_-.:" -> {
                var j = i
                while (j < n && (text[j].isLetterOrDigit() || text[j] in "_-.:+")) j++
                val token = text.substring(i, j)
                val style = when {
                    token == "true" || token == "false" ->
                        SpanStyle(color = CodeIndigo, fontWeight = FontWeight.Medium)
                    token.any(Char::isDigit) -> SpanStyle(color = CodeAmber)
                    else -> null
                }
                if (style != null) builder.addStyle(style, i, j)
                i = j
            }
            else -> i++
        }
    }
    return builder.toAnnotatedString()
}

private val TomlVisualTransformation = VisualTransformation { value ->
    TransformedText(highlightToml(value.text), OffsetMapping.Identity)
}

/** 弹层里只有一块代码面板：无标题无外壳，靠遮罩点击 / 右上角 X 关闭。 */
@Composable
private fun CodePanelDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = CodeBg,
            contentColor = CodeFg,
            border = BorderStroke(0.5.dp, CodeLine),
        ) {
            content()
        }
    }
}

/** 面板顶部工具排：右侧图标按钮组（可带左侧主操作）。 */
@Composable
private fun PanelToolbar(
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Box(Modifier)
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun CodeAction(
    icon: ImageVector,
    label: String,
    tint: Color = CodeFg,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * TOML 导入对话框：可编辑的高亮代码面板，「导入」在工具排左侧，右上角文件选择 / 关闭。
 * 解析失败时弹层保持打开，错误条贴面板底部（终端风格红条）。
 */
@Composable
fun TomlImportDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onImported: (NetworkConfig) -> Unit,
) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(TextFieldValue(initialText)) }
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
                    value = TextFieldValue(content)
                    error = null
                }
            },
            onFailure = { e -> error = "读取文件失败：${e.message}" },
        )
    }

    CodePanelDialog(onDismiss = onDismiss) {
        Column {
            PanelToolbar(
                leading = {
                    Button(
                        enabled = value.text.isNotBlank(),
                        onClick = { tryParse(value.text.trim()) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp).padding(start = 4.dp),
                    ) {
                        Text("导入", style = MaterialTheme.typography.labelLarge)
                    }
                },
                actions = {
                    CodeAction(AppIcons.FolderOpen, "从文件选择") { pickFile.launch("*/*") }
                    CodeAction(AppIcons.Close, "关闭") { onDismiss() }
                },
            )
            HorizontalDivider(thickness = 0.5.dp, color = CodeLine)
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                textStyle = MonoStyle.copy(color = CodeFg),
                cursorBrush = SolidColor(CodeIndigo),
                visualTransformation = TomlVisualTransformation,
                // 上限防横屏/小屏时输入区过高
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 380.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty()) {
                            Text(
                                "# instance_name = ...\n[network_identity]\nnetwork_name = \"default\"",
                                style = MonoStyle.copy(fontSize = 12.sp, color = CodeMuted),
                            )
                        }
                        inner()
                    }
                },
            )
            error?.let { err ->
                HorizontalDivider(thickness = 0.5.dp, color = CodeLine)
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = CodeErrorFg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState())
                        .background(CodeErrorBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * TOML 导出对话框：整块高亮代码面板即弹层，复制 / 分享 / 存为文件 / 关闭都在面板右上角。
 */
@Composable
fun TomlExportDialog(
    toml: String,
    networkName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val fileName = suggestedFileName(networkName)

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(toml.toByteArray())
            } ?: throw IllegalStateException("无法写入所选位置")
        }.fold(
            onSuccess = { toast("已保存 $fileName") },
            onFailure = { e -> toast("保存失败：${e.message}") },
        )
    }

    CodePanelDialog(onDismiss = onDismiss) {
        Column {
            PanelToolbar(
                actions = {
                    CodeAction(
                        icon = if (copied) AppIcons.Check else AppIcons.ContentCopy,
                        label = if (copied) "已复制" else "复制",
                        tint = if (copied) CodeEmerald else CodeFg,
                        onClick = {
                            clipboard.setText(AnnotatedString(toml))
                            copied = true
                            toast("已复制到剪贴板")
                        },
                    )
                    CodeAction(AppIcons.Share, "分享") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, fileName)
                            putExtra(Intent.EXTRA_TEXT, toml)
                        }
                        context.startActivity(Intent.createChooser(send, "分享 TOML 配置"))
                    }
                    CodeAction(AppIcons.Download, "存为文件") { saveFile.launch(fileName) }
                    CodeAction(AppIcons.Close, "关闭") { onDismiss() }
                },
            )
            HorizontalDivider(thickness = 0.5.dp, color = CodeLine)
            SelectionContainer {
                Text(
                    highlightToml(toml),
                    style = MonoStyle.copy(fontSize = 12.sp, color = CodeFg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 网络名转安全文件名。 */
private fun suggestedFileName(networkName: String): String =
    networkName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_', '.')
        .ifBlank { "easytier" } + ".toml"
