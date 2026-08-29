package com.easytier.android.core.vpn

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import android.util.Log
import com.easytier.android.EasyTierApp
import com.easytier.android.MainActivity
import com.easytier.android.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 下滑快捷设置磁贴：一键开关服务（引擎 + 勾选网络）。
 * - 点击切换：运行中则停止；未运行且有勾选网络则启动。
 * - 磁贴面板展开期间跟随服务状态与勾选情况实时刷新；
 *   没有勾选网络时磁贴置灰不可用。
 */
class QuickSettingsTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = applicationContext as? EasyTierApp ?: return
        update(app.container.vpnController.serviceRunning.value)
        // 面板展开期间实时跟随状态变化
        observeJob = app.applicationScope.launch {
            combine(
                app.container.vpnController.serviceRunning,
                app.container.networksRepository.networks,
            ) { running, networks -> running to networks.any { it.enabled } }
                .distinctUntilChanged()
                .collect { (running, hasEnabled) -> update(running, hasEnabled) }
        }
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? EasyTierApp ?: return
        val controller = app.container.vpnController
        Log.d(TAG, "onClick: running=${controller.serviceRunning.value}")
        if (controller.serviceRunning.value) {
            controller.stopService()
            return
        }
        app.applicationScope.launch {
            val enabled = app.container.networksRepository.networks.first().filter { it.enabled }
            if (enabled.isEmpty()) {
                Log.d(TAG, "启动取消：没有勾选的网络")
                mainHandler.post {
                    Toast.makeText(applicationContext, R.string.tile_no_network, Toast.LENGTH_SHORT).show()
                }
                update(false, hasEnabledNetworks = false)
                return@launch
            }
            // VPN 被其他应用抢占或授权被回收后 establish 会静默失败，而磁贴弹不了系统授权框：
            // 转交 MainActivity 申请授权、通过后自动启动服务（仅引擎模式无需授权）
            val vpnEnabled = app.container.settingsRepository.settings.first().enableVpn
            if (vpnEnabled && controller.needsPermission() != null) {
                requestPermissionInApp()
            } else {
                controller.startService(enabled)
            }
        }
    }

    /** 拉起应用申请系统 VPN 授权，通过后由应用自动继续启动服务。 */
    private fun requestPermissionInApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_REQUEST_VPN_AND_START, true),
            )
        }.onFailure { Log.e(TAG, "拉起应用申请 VPN 授权失败", it) }
        mainHandler.post {
            Toast.makeText(applicationContext, R.string.tile_need_vpn_permission, Toast.LENGTH_LONG).show()
        }
    }

    /** 刷新磁贴状态；勾选情况未知时按可用来处理。 */
    private fun update(running: Boolean, hasEnabledNetworks: Boolean = true) {
        val tile = qsTile ?: return
        tile.state = when {
            !hasEnabledNetworks -> Tile.STATE_UNAVAILABLE
            running -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "QuickSettingsTile"
    }
}
