package com.easytier.android.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.easytier.android.core.engine.EasyTierEngine
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.data.store.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * VPN / 网络实例启停编排器。
 *
 * 流程：启动核心实例 -> 状态驱动等待 DHCP 分配虚拟 IP -> 拉起前台 VPN 服务（TUN）。
 * IP 分配在弱网下可能远超 30s，不做一次性超时等待，而是监听 states 流直到就绪。
 *
 * 服务与网络解耦：实例可多个同时运行；只有最后一个实例停止时才关闭 VPN 服务，
 * 避免停掉一个网络把其他网络的 TUN 一起带崩。
 */
class VpnController(
    private val context: Context,
    private val engine: EasyTierEngine,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 待建立 TUN 的网络（name -> 配置），IP 就绪即建，实例停止/出错则移除。 */
    private val pendingVpns = mutableMapOf<String, SavedNetwork>()

    /** 已启动实例的配置哈希（name -> hash），用于判断编辑后是否需要重启实例。 */
    private val runningConfigHashes = mutableMapOf<String, Int>()

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

    /**
     * 启动单个网络实例。应用层设置（全局 SOCKS5）在此统一注入生成的 TOML；
     * withVpn 由调用方按设置页「VPN 模式」决定（开机自启传 false 时同样跳过 TUN）。
     */
    fun startNetwork(network: SavedNetwork, withVpn: Boolean) {
        scope.launch {
            val settings = settingsRepository.settings.first()
            // 应用层设置覆盖网络配置里的同名项（编辑器已不再暴露 SOCKS5）
            val config = network.config.copy(
                enableSocks5 = settings.enableSocks5,
                socks5Port = if (settings.enableSocks5) settings.socks5Port else null,
            )
            val effective = network.copy(config = config)
            val name = config.networkName
            // 已在运行：同配置只补建 VPN；配置已变（编辑后保存并运行）则重启实例使新配置生效
            val configChanged = runningConfigHashes[name] != null &&
                runningConfigHashes[name] != config.hashCode()
            val alreadyRunning =
                engine.states.value[name] is InstanceState.Running
            if (alreadyRunning && configChanged) {
                engine.stop(name)
            }
            if (!alreadyRunning || configChanged) {
                engine.start(effective, TomlGenerator.generate(config))
                    .onFailure { Log.e(TAG, "实例启动失败: ${it.message}"); return@launch }
                runningConfigHashes[name] = config.hashCode()
            }
            if (withVpn) {
                if (needsPermission() != null) {
                    Log.w(TAG, "VPN 未授权，实例已启动但未建立 TUN")
                } else {
                    pendingVpns[name] = effective
                }
            }
        }
    }

    /** 停止单个网络实例；仅当没有其他运行中/启动中的实例时才关闭 VPN 服务。 */
    fun stopNetwork(network: SavedNetwork) {
        scope.launch {
            val name = network.config.networkName
            pendingVpns.remove(name)
            runningConfigHashes.remove(name)
            engine.stop(name)
            val othersActive = engine.states.value.values.any {
                it is InstanceState.Running || it is InstanceState.Starting
            }
            if (!othersActive) EasyTierVpnService.stop(context)
        }
    }

    /** 启动一批网络（服务级开关 / 开机自启用）。 */
    fun startNetworks(networks: List<SavedNetwork>, withVpn: Boolean) {
        networks.forEach { startNetwork(it, withVpn) }
    }

    /** 停止全部网络与服务。 */
    fun stopAll() {
        scope.launch {
            pendingVpns.clear()
            runningConfigHashes.clear()
            EasyTierVpnService.stop(context)
            engine.stopAll()
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
