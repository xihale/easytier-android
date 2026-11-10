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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 网络 / 服务编排器。
 *
 * 服务与网络解耦：
 * - 网络 = 勾选的配置条目（enabled），只是标记，不代表引擎在跑。
 * - 服务 = 引擎 + 系统 VPN（TUN）。开启服务 = 启动引擎并拉起全部勾选的网络；
 *   没有勾选的网络时报错并不启动。关闭服务 = 停掉引擎与 TUN。
 * - 勾选/取消勾选时同步引擎：服务运行中勾选则启动该实例，取消则停止该实例。
 */
class VpnController(
    private val context: Context,
    private val engine: EasyTierEngine,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serviceRunning = MutableStateFlow(false)

    /** 服务（引擎 + TUN）开关状态，首页 Hero 卡绑定。 */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** 已启动实例的运行时配置（name -> config），用于判断编辑后是否需要重启、以及端口占用避让。 */
    private val runningConfigs = mutableMapOf<String, NetworkConfig>()

    /** 已建立 TUN 的实例，避免状态轮询重复建立。 */
    private val tunEstablished = mutableSetOf<String>()

    /** 串行化启动与 TUN 建立：避免并发读到彼此注册前的旧快照。 */
    private val startMutex = Mutex()

    /** 设置页「启动 VPN」开关缓存：关闭时不建立/保持 TUN，仅跑引擎。 */
    @Volatile
    private var vpnEnabled = true

    /** 是否需要用户授权 VPN（返回非 null 的 Intent 需交给 Activity 发起）。 */
    fun needsPermission(): Intent? = VpnService.prepare(context)

    init {
        scope.launch {
            // VPN 开关变化实时生效：关闭即拆 TUN；开启且服务运行中则补建
            settingsRepository.settings
                .map { it.enableVpn }
                .distinctUntilChanged()
                .collect { enabled ->
                    vpnEnabled = enabled
                    startMutex.withLock {
                        if (!enabled) {
                            tunEstablished.clear()
                            EasyTierVpnService.stopTun(context)
                            if (_serviceRunning.value) {
                                EasyTierForegroundService.start(context, noTunText(runningConfigs.keys))
                            }
                        } else if (_serviceRunning.value) {
                            EasyTierForegroundService.stop(context)
                            engine.states.value.forEach { (name, state) ->
                                (state as? InstanceState.Running)?.info?.let { maybeEstablishTun(name, it) }
                            }
                        }
                    }
                }
        }
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
        if (!vpnEnabled) return
        val config = runningConfigs[name] ?: return
        if (name in tunEstablished) return
        val ipCidr = info.myNodeInfo?.virtualIpv4?.toCidrString() ?: return
        tunEstablished.add(name)
        val cidrs = config.proxyCidrs.map { it.substringBefore("->").trim() }
        runCatching {
            EasyTierVpnService.start(
                context, name, ipCidr, cidrs,
                magicDns = config.enableMagicDns,
                // 后缀与生成器同口径：显式配置优先，否则网络名（清洗后）
                dnsZone = TomlGenerator.effectiveDnsZone(config),
            )
            EasyTierForegroundService.stop(context)
        }.onFailure { Log.e(TAG, "VPN 服务启动失败", it) }
    }

    /**
     * 启动单个网络实例（引擎内），不改变服务开关。
     * 应用层设置（全局 SOCKS5）在此统一注入生成的 TOML。
     */
    fun startNetwork(network: SavedNetwork) {
        scope.launch {
            startMutex.withLock {
                startNetworkLocked(network)
            }
        }
    }

    /** 实际启动逻辑，调用方需持有 [startMutex]。 */
    private suspend fun startNetworkLocked(network: SavedNetwork) {
        val settings = settingsRepository.settings.first()
        // 应用层设置覆盖网络配置里的同名项（编辑器已不再暴露 SOCKS5）；
        // DNS 上游与 SOCKS5 同为全局设置，注入全部网络
        val config = network.config.copy(
            enableSocks5 = settings.enableSocks5,
            socks5Port = if (settings.enableSocks5) settings.socks5Port else null,
            dnsForwardServers = settings.dnsServers.takeIf { it.isNotEmpty() },
        )
        val effective = network.copy(config = config)
        val name = config.networkName
        // 多实例端口避让：监听/SOCKS5/VPN Portal 端口被其他运行实例占用时改用空闲端口，
        // 否则第二个网络会因 Address already in use 启动失败。
        // 自身排除在 PortAvoidance 内完成（含单测）：否则重启/同步配置时会把
        // 自身端口误判为冲突，导致端口持续漂移（issue #1）。
        val finalConfig = PortAvoidance.resolve(
            config = config,
            runningConfigs = runningConfigs.values,
            selfName = name,
            socks5Enabled = settings.enableSocks5,
            requestedSocks5Port = settings.socks5Port,
        )
        // 已在运行：同配置只登记；配置已变（编辑后保存）则重启实例使新配置生效
        val configChanged = runningConfigs[name] != null &&
            runningConfigs[name] != finalConfig
        val alreadyRunning = engine.states.value[name] is InstanceState.Running
        if (alreadyRunning && configChanged) {
            engine.stop(name)
        }
        if (!alreadyRunning || configChanged) {
            engine.start(effective, TomlGenerator.generate(finalConfig))
                .onFailure { Log.e(TAG, "实例启动失败: ${it.message}"); return }
            runningConfigs[name] = finalConfig
            tunEstablished.remove(name)
            if (!vpnEnabled && _serviceRunning.value) {
                EasyTierForegroundService.start(context, noTunText(runningConfigs.keys))
            }
        }
    }

    /** 停止单个网络实例；不影响服务开关。 */
    fun stopNetwork(network: SavedNetwork) {
        scope.launch {
            startMutex.withLock { stopNetworkLocked(network) }
        }
    }

    private suspend fun stopNetworkLocked(network: SavedNetwork) {
        val name = network.config.networkName
        runningConfigs.remove(name)
        tunEstablished.remove(name)
        engine.stop(name)
        if (!vpnEnabled && _serviceRunning.value && runningConfigs.isNotEmpty()) {
            EasyTierForegroundService.start(context, noTunText(runningConfigs.keys))
        }
    }

    /**
     * 勾选状态变化：服务运行中则同步引擎（勾选启动、取消停止）。
     * 服务未运行时只改持久化（由仓库负责），启动服务时再统一拉起。
     */
    fun onEnabledChanged(network: SavedNetwork, enabled: Boolean) {
        if (!_serviceRunning.value) return
        if (enabled) startNetwork(network) else stopNetwork(network)
    }

    /**
     * 启动服务（引擎 + TUN），拉起全部勾选的网络。
     * 没有勾选的网络时报错并不启动。
     */
    fun startService(enabledNetworks: List<SavedNetwork>): Result<Unit> {
        if (enabledNetworks.isEmpty()) {
            return Result.failure(IllegalStateException("没有勾选的网络，请先勾选要加入服务的网络"))
        }
        _serviceRunning.value = true
        scope.launch {
            startMutex.withLock {
                // 停掉已不在勾选集合里的运行实例
                val keep = enabledNetworks.map { it.config.networkName }.toSet()
                runningConfigs.keys.filter { it !in keep }.forEach { name ->
                    engine.stop(name)
                    runningConfigs.remove(name)
                    tunEstablished.remove(name)
                }
                enabledNetworks.forEach { startNetworkLocked(it) }
                if (!vpnEnabled) {
                    EasyTierForegroundService.start(
                        context,
                        noTunText(enabledNetworks.map { it.config.networkName }),
                    )
                } else {
                    // 已在跑且拿到 IP 的直接补 TUN
                    engine.states.value.forEach { (name, state) ->
                        (state as? InstanceState.Running)?.info?.let { maybeEstablishTun(name, it) }
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    /** 停止服务（引擎 + TUN 全停）。 */
    fun stopService() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock {
                runningConfigs.clear()
                tunEstablished.clear()
            }
            EasyTierVpnService.stopTun(context)
            EasyTierForegroundService.stop(context)
            engine.stopAll()
        }
    }

    /** 开机自启用：直接启动服务（引擎 + 勾选网络）。 */
    fun startServiceWithNetworks(networks: List<SavedNetwork>) {
        startService(networks.filter { it.enabled })
    }

    /** 外部停止（通知栏「停止」/ VPN 被系统撤销 / TUN 建立失败）：停服务并停全部网络。 */
    fun onStoppedExternally() {
        _serviceRunning.value = false
        scope.launch {
            startMutex.withLock {
                runningConfigs.clear()
                tunEstablished.clear()
            }
            EasyTierVpnService.stopTun(context)
            EasyTierForegroundService.stop(context)
            engine.stopAll()
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
            EasyTierForegroundService.stop(context)
            engine.stopAll()
        }.invokeOnCompletion { scope.cancel() }
    }

    /** 无 TUN 模式的前台通知文案。 */
    private fun noTunText(names: Collection<String>): String =
        if (names.isEmpty()) "运行中 (无 TUN 模式)" else "运行中 (无 TUN) · ${names.joinToString(", ")}"

    companion object {
        private const val TAG = "VpnController"
    }
}
