package com.easytier.android.ui.components

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 返回一个启动函数：VPN 未授权时先弹系统授权框，授权通过后自动继续执行 [block]；
 * 已授权则直接执行。用于「启动网络」「保存并运行」等入口，
 * 避免 VpnController 因未授权静默跳过 TUN 建立导致网络"启动了但用不了"。
 *
 * pending 用 rememberSaveable：授权框遮挡期间 Activity 可能被回收重建，
 * 普通 remember 会丢失状态导致授权成功后回调变空操作。
 */
@Composable
fun rememberWithVpnPermission(block: () -> Unit): () -> Unit {
    val context: Context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf(false) }
    val currentBlock by rememberUpdatedState(block)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (pending && result.resultCode == Activity.RESULT_OK) {
            pending = false
            currentBlock()
        }
    }
    return {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pending = true
            launcher.launch(prepareIntent)
        } else {
            currentBlock()
        }
    }
}
