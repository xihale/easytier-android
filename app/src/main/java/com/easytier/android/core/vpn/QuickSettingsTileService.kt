package com.easytier.android.core.vpn

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.easytier.android.EasyTierApp
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
        if (controller.serviceRunning.value) {
            controller.stopService()
            return
        }
        app.applicationScope.launch {
            val enabled = app.container.networksRepository.networks.first().filter { it.enabled }
            if (enabled.isEmpty()) {
                mainHandler.post {
                    Toast.makeText(applicationContext, R.string.tile_no_network, Toast.LENGTH_SHORT).show()
                }
                update(false, hasEnabledNetworks = false)
            } else {
                controller.startService(enabled)
            }
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
}
