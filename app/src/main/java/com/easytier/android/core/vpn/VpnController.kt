package com.easytier.android.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.easytier.android.core.engine.EasyTierEngine
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.data.store.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 网络 / 服务编排器。
 *
 * 服务与网络解耦，两者互不影响：
 * - 网络 = 核心实例，可独立启停（startNetwork / stopNetwork），无需服务在运行。
 * - 服务 = 系统 VPN（TUN）前台服务，只为运行中的网络建立 TUN；
 *   没有运行中的网络时启动服务会报错并不启动。
 * - 停止服务只关闭 TUN，网络实例保持运行；停止网络也不改动服务开关。
 */
class VpnController(
    private val context: Context,
    private val engine: EasyTierEngine,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serviceRunning = MutableStateFlow(false)

    /** 服务（TUN）开关状态，首页 Hero 卡绑定。 */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** 已启动实例的运行时配置（name -> config），用于判断编辑后是否需要重启、以及端口占用避让。 */
    private val runningConfigs = mutableMapOf<String, NetworkConfig>()

    /** 已建立 TUN 的实例，避免状态轮询重复建立。 */
    private val tunEstablished = mutableSetOf<String>()

    /** 串行化启动与 TUN 建立：避免并发读到彼此注册前的旧快照。 */
    private val startMutex = Mutex()

    /** 是否需要用户授权 VPN（返回非 null 的 Intent 需交给 Activity 发起）。 */
    fun needsPermission(): Intent? = VpnService.prepare(context)

    init {
        scope.launch {
            // 服务开启期间，实例一拿到虚拟 IP 就自动补建 TUN
            engine.states.collect { states ->
                if (!_serviceRunning.value) return@collect
                startMutex.withLock {
                    states.forEach { (name, state) ->
                        (state as? InstanceState.Running)?.info?.let { maybeEstablishTun(name, it) }
                    }
                }
            }
        }
    }

    /** 为运行中且已拿到虚拟 IP 的实例建立 TUN（幂等）。调用方需持有 [startMutex]。 */
    private fun maybeEstablishTun(name: String, info: NetworkInstanceRunningInfo) {
        val config = runningConfigs[name] ?: return
        if (name in tunEstablished) return
        val ipCidr = info.myNodeInfo?.virtualIpv4?.toCidrString() ?: return
        tunEstablished.add(name)
        val cidrs = config.proxyCidrs.map { it.substringBefore("->").trim() }
        runCatching { EasyTierVpnService.start(context, name, ipCidr, cidrs) }
            .onFailure { Log.e(TAG, "VPN 服务启动失败", it) }
    }

    /**
     * 启动单个网络实例（与服务无关，不建立 TUN）。
     * 应用层设置（全局 SOCKS5）在此统一注入生成的 TOML。
     */
    fun startNetwork(network: SavedNetwork) {
        scope.launch {
            startMutex.withLock {
                val settings = settingsRepository.settings.first()
                // 应用层设置覆盖网络配置里的同名项（编辑器已不再暴露 SOCKS5）
                val config = network.config.copy(
                    enableSocks5 = settings.enableSocks5,
                    socks5Port = if (settings.enableSocks5) settings.socks5Port else null,
                )
                val effective = network.copy(config = config)
                val name = config.networkName
                // 多实例端口避让：监听端口与 SOCKS5 端口被其他运行实例占用时改用空闲端口，
                // 否则第二个网络会因 Address already in use 启动失败
                val usedPorts = runningConfigs.values
                    .flatMap { it.listenerUrls }
                    .mapNotNull { it.substringAfterLast(':').toIntOrNull() }
                    .toMutableSet()
                runningConfigs.values.forEach {
                    if (it.enableSocks5 == true) it.socks5Port?.let { p -> usedPorts.add(p) }
                }
                val listeners = if (config.listenerUrls.any {
                        it.substringAfterLast(':').toIntOrNull() in usedPorts
                    }
                ) {
                    config.listenerUrls.map { url -> avoidPortConflict(url, usedPorts) }
                } else {
                    config.listenerUrls
                }
                val effectiveConfig = config.copy(listenerUrls = listeners)
                var socks5Port = if (settings.enableSocks5) settings.socks5Port else null
                if (socks5Port != null) {
                    var candidate = socks5Port
                    while (candidate in usedPorts) candidate += 1
                    usedPorts.add(candidate)
                    socks5Port = candidate
                }
                val finalConfig = effectiveConfig.copy(
                    enableSocks5 = socks5Port != null,
                    socks5Port = socks5Port,
                )
                // 已在运行：同配置只登记；配置已变（编辑后保存并运行）则重启实例使新配置生效
                val configChanged = runningConfigs[name] != null &&
                    runningConfigs[name] != finalConfig
                val alreadyRunning = engine.states.value[name] is InstanceState.Running
                if (alreadyRunning && configChanged) {
                    engine.stop(name)
                }
                if (!alreadyRunning || configChanged) {
                    engine.start(effective, TomlGenerator.generate(finalConfig))
                        .onFailure { Log.e(TAG, "实例启动失败: ${it.message}"); return@launch }
                    runningConfigs[name] = finalConfig
                    tunEstablished.remove(name)
                }
                // TUN 由服务开关驱动：服务在运行时，states 收集器会在 IP 就绪后自动建立
            }
        }
    }

    /** 停止单个网络实例；不影响服务开关（无其他实例时顺带关闭已无意义的 TUN）。 */
    fun stopNetwork(network: SavedNetwork) {
        scope.launch {
            startMutex.withLock {
                val name = network.config.networkName
                runningConfigs.remove(name)
                tunEstablished.remove(name)
                engine.stop(name)
                val othersActive = engine.states.value.values.any {
                    it is InstanceState.Running || it is InstanceState.Starting
                }
                if (!othersActive) {
                    // TUN 必须挂在实例上；仅停前台服务，服务开关状态保留
                    EasyTierVpnService.stopTun(context)
                }
            }
        }
    }

    /**
     * 启动服务（系统 VPN / TUN）。
     * 没有任何运行中（或启动中）的网络时报错并不启动。
     */
    fun startService(): Result<Unit> {
        val states = engine.states.value
        val anyActive = states.values.any {
            it is InstanceState.Running || it is InstanceState.Starting
        }
        if (!anyActive) {
            return Result.failure(IllegalStateException("没有运行中的网络，请先打开网络开关"))
        }
        _serviceRunning.value = true
        scope.launch {
            startMutex.withLock {
                states.forEach { (name, state) ->
                    (state as? InstanceState.Running)?.info?.let { maybeEstablishTun(name, it) }
                }
            }
        }
        return Result.success(Unit)
    }

    /** 停止服务（仅关闭 TUN 与前台服务）；网络实例保持运行。 */
    fun stopService() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock { tunEstablished.clear() }
            EasyTierVpnService.stopTun(context)
        }
    }

    /** 开机自启用：先标记服务运行意图，再启动网络；实例就绪后自动建立 TUN。 */
    fun startServiceWithNetworks(networks: List<SavedNetwork>) {
        _serviceRunning.value = true
        networks.forEach { startNetwork(it) }
    }

    /** 停止全部网络与服务。 */
    fun stopAll() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock {
                runningConfigs.clear()
                tunEstablished.clear()
            }
            EasyTierVpnService.stopTun(context)
            engine.stopAll()
        }
    }

    /** 通知栏「停止」等外部停止：停服务并停全部网络。 */
    fun onStoppedExternally() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock {
                runningConfigs.clear()
                tunEstablished.clear()
            }
            engine.stopAll()
        }
    }

    /** TUN 被系统撤销（onRevoke）：仅重置服务状态，网络实例不受影响。 */
    fun onTunRevoked() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock { tunEstablished.clear() }
        }
    }

    fun shutdown() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock {
                runningConfigs.clear()
                tunEstablished.clear()
            }
            EasyTierVpnService.stopTun(context)
            engine.stopAll()
        }.invokeOnCompletion { scope.cancel() }
    }

    companion object {
        private const val TAG = "VpnController"

        /** 监听 URL 的端口被占用时，从原端口 +10 起找一个未占用端口。 */
        private fun avoidPortConflict(url: String, usedPorts: MutableSet<Int>): String {
            val port = url.substringAfterLast(':').toIntOrNull() ?: return url
            if (port !in usedPorts) return url
            var candidate = port
            while (candidate in usedPorts) candidate += 10
            usedPorts.add(candidate)
            return url.substringBeforeLast(':') + ":" + candidate
        }
    }
}
