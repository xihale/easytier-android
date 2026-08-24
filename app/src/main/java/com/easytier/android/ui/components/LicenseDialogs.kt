package com.easytier.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.easytier.android.R

/** 开源许可条目：组件名 + 协议 + 主页 + res/raw 内的许可证全文。 */
private data class OssLicense(
    val name: String,
    val license: String,
    val url: String,
    val textRes: Int,
)

private val OSS_LICENSES = listOf(
    // EasyTier 核心以预编译 .so 形式动态链接，LGPLv3 §4a 要求随分发副本附带显著声明与许可全文
    OssLicense(
        name = "EasyTier 核心引擎",
        license = "LGPL-3.0",
        url = "github.com/EasyTier/EasyTier",
        textRes = R.raw.license_lgpl_3,
    ),
    OssLicense(
        name = "EasyTier Android",
        license = "MIT",
        url = "github.com/xihale/easytier-android",
        textRes = R.raw.license_mit,
    ),
)

/** 开源许可对话框：先列组件清单，点进查看许可证全文。 */
@Composable
fun OssLicensesDialog(onDismiss: () -> Unit) {
    var viewing by remember { mutableStateOf<OssLicense?>(null) }
    val entry = viewing
    if (entry == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("开源许可") },
            text = {
                Column {
                    OSS_LICENSES.forEach { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewing = e }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${e.license} · ${e.url}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("关闭") }
            },
        )
    } else {
        LicenseTextDialog(entry = entry, onBack = { viewing = null })
    }
}

/** 许可证全文查看：限高滚动；文本 ≤ 42KB，一次性读入无压力。 */
@Composable
private fun LicenseTextDialog(entry: OssLicense, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(entry.textRes) {
        context.resources.openRawResource(entry.textRes).bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Column {
                Text(entry.name)
                Text(
                    entry.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onBack) { Text("返回") }
        },
    )
}
