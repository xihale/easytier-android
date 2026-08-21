package com.easytier.android.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.easytier.android.core.engine.EasyTierEngine
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.SavedNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * VPN / 网络实例启停编排器。
 *
 * 流程：启动核心实例 -> 状态驱动等待 DHCP 分配虚拟 IP -> 拉起前台 VPN 服务（TUN）。
 * IP 分配在弱网下可能远超 30s，不做一次性超时等待，而是监听 states 流直到就绪。
 */
class VpnController(
    private val context: Context,
    private val engine: EasyTierEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 待建立 TUN 的网络（name -> 配置），IP 就绪即建，实例停止/出错则移除。 */
    private val pendingVpns = mutableMapOf<String, SavedNetwork>()

    init {
        scope.launch {
            engine.states.collect { states ->
                pendingVpns.entries.toList().forEach { (name, network) ->
                    when (val state = states[name]) {
                        is InstanceState.Running -> {
                            val ipCidr = state.info.myNodeInfo?.virtualIpv4?.toCidrString()
                            if (ipCidr != null) {
                                val cidrs = network.config.proxyCidrs
                                    .map { it.substringBefore("->").trim() }
                                runCatching { EasyTierVpnService.start(context, name, ipCidr, cidrs) }
                                    .onFailure { Log.e(TAG, "VPN 服务启动失败", it) }
                                pendingVpns.remove(name)
                            }
                        }
                        is InstanceState.Error, InstanceState.Stopped, null -> pendingVpns.remove(name)
                        InstanceState.Starting -> Unit
                    }
                }
            }
        }
    }

    /** 是否需要用户授权 VPN（返回非 null 的 Intent 需交给 Activity 发起）。 */
    fun needsPermission(): Intent? = VpnService.prepare(context)

    /** 启动网络实例（含 VPN，若已授权）。未授权时仅启动实例并跳过 TUN，由 UI 层先发起授权。 */
    fun startNetwork(network: SavedNetwork, withVpn: Boolean) {
        scope.launch {
            val name = network.config.networkName
            // 授权回调后再次进入时实例可能已在运行，只补建 VPN，不重复 start
            val alreadyRunning =
                engine.states.value[name] is InstanceState.Running
            if (!alreadyRunning) {
                engine.start(network, TomlGenerator.generate(network.config))
                    .onFailure { Log.e(TAG, "实例启动失败: ${it.message}"); return@launch }
            }
            if (withVpn) {
                if (needsPermission() != null) {
                    Log.w(TAG, "VPN 未授权，实例已启动但未建立 TUN")
                } else {
                    pendingVpns[name] = network
                }
            }
        }
    }

    /** 停止网络实例及其 VPN。 */
    fun stopNetwork(network: SavedNetwork) {
        scope.launch {
            val name = network.config.networkName
            pendingVpns.remove(name)
            EasyTierVpnService.stop(context)
            engine.stop(name)
        }
    }

    fun shutdown() {
        scope.launch {
            EasyTierVpnService.stop(context)
            engine.stopAll()
        }.invokeOnCompletion { scope.cancel() }
    }

    companion object {
        private const val TAG = "VpnController"
    }
}
