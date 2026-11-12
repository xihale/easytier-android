package com.easytier.android.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.easytier.android.R
import com.easytier.android.core.engine.EasyTierEngine
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.data.store.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
@OptIn(FlowPreview::class)
class VpnController(
    private val context: Context,
    private val engine: EasyTierEngine,
    private val settingsRepository: SettingsRepository,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "VpnController 后台协程异常", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private val _serviceRunning = MutableStateFlow(false)

    /** 服务（引擎 + TUN）开关状态，首页 Hero 卡绑定。 */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** 已启动实例的原始条目（用于全局 SOCKS5 / DNS 变更时平滑热重载）。 */
    private val runningNetworks = ConcurrentHashMap<String, SavedNetwork>()

    /** 已启动实例的运行时配置（name -> config），用于判断编辑后是否需要重启、以及端口占用避让。 */
    private val runningConfigs = ConcurrentHashMap<String, NetworkConfig>()

    /** 已下发给系统 VPN 的 TUN 规格快照：用于无锁幂等比对，仅在 IP/路由/MTU 变化时热更新。 */
    private val establishedTunSpecs = ConcurrentHashMap<String, TunSpec>()

    /** 串行化启动与 TUN 建立：避免并发读到彼此注册前的旧快照。 */
    private val startMutex = Mutex()

    /** 设置页「启动 VPN」开关缓存：关闭时不建立/保持 TUN，仅跑引擎。 */
    @Volatile
    private var vpnEnabled = true

    /** 物理网络切换感知与低功耗 Wi-Fi 组播脉冲调度。 */
    private val connectivityMonitor = NetworkConnectivityMonitor(
        context = context,
        scope = scope,
        onPhysicalNetworkChanged = { engine.onPhysicalNetworkChanged() },
    )

    private data class GlobalNetOptions(
        val enableSocks5: Boolean,
        val socks5Port: Int,
        val dnsServers: List<String>,
    )

    /** 是否需要用户授权 VPN（返回非 null 的 Intent 需交给 Activity 发起）。 */
    fun needsPermission(): Intent? = VpnService.prepare(context)

    /** 应用回到前台时：若服务在跑，触发一次轻量 Wi-Fi 局域网组播发现脉冲。 */
    fun onAppForeground() {
        if (_serviceRunning.value) {
            connectivityMonitor.triggerDiscoveryBurst()
        }
    }

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
                            establishedTunSpecs.clear()
                            EasyTierVpnService.stopTun(context)
                            if (_serviceRunning.value) {
                                EasyTierForegroundService.start(context, noTunText(runningConfigs.keys))
                            }
                        } else if (_serviceRunning.value) {
                            engine.states.value.forEach { (name, state) ->
                                (state as? InstanceState.Running)?.info?.let { reconcileTunLocked(name, it) }
                            }
                            if (establishedTunSpecs.isEmpty() && runningConfigs.isNotEmpty()) {
                                EasyTierForegroundService.start(
                                    context,
                                    context.getString(R.string.vpn_state_starting),
                                )
                            } else if (establishedTunSpecs.isNotEmpty()) {
                                EasyTierForegroundService.stop(context)
                            }
                        }
                    }
                }
        }
        scope.launch {
            // 全局 SOCKS5 / DNS 上游在服务运行中修改时防抖同步到运行实例，避免手工重启
            settingsRepository.settings
                .map { GlobalNetOptions(it.enableSocks5, it.socks5Port, it.dnsServers) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(600L)
                .collect {
                    if (!_serviceRunning.value) return@collect
                    startMutex.withLock {
                        if (!_serviceRunning.value || runningNetworks.isEmpty()) return@withLock
                        val snapshot = runningNetworks.values.toList()
                        snapshot.forEach { startNetworkLocked(it) }
                    }
                }
        }
        scope.launch {
            // 服务开启期间，实例拿到/变更虚拟 IP 或远端子网代理路由变化时自动建立/热更新 TUN。
            // 先走无锁快路径比对 TunSpec，避免每 2s 流量计数刷新时无意义争抢 startMutex。
            engine.states.collect { states ->
                if (!_serviceRunning.value || !vpnEnabled) return@collect
                if (!hasPendingTunChanges(states)) return@collect
                startMutex.withLock {
                    if (!_serviceRunning.value || !vpnEnabled) return@withLock
                    states.forEach { (name, state) ->
                        (state as? InstanceState.Running)?.info?.let { reconcileTunLocked(name, it) }
                    }
                }
            }
        }
    }

    /** 无锁快路径：判断当前引擎状态是否存在需要新建或更新的 [TunSpec]。 */
    private fun hasPendingTunChanges(states: Map<String, InstanceState>): Boolean {
        for ((name, state) in states) {
            val info = (state as? InstanceState.Running)?.info ?: continue
            val config = runningConfigs[name] ?: continue
            val planned = TunRoutePlanner.plan(name, config, info) ?: continue
            if (establishedTunSpecs[name] != planned) {
                return true
            }
        }
        return false
    }

    /** 为运行中且已拿到虚拟 IP 的实例建立或热更新 TUN（幂等）。调用方需持有 [startMutex]。 */
    private fun reconcileTunLocked(name: String, info: NetworkInstanceRunningInfo) {
        if (!vpnEnabled) return
        val config = runningConfigs[name] ?: return
        val planned = TunRoutePlanner.plan(name, config, info) ?: return
        if (establishedTunSpecs[name] == planned) return
        establishedTunSpecs[name] = planned
        runCatching {
            EasyTierVpnService.start(context, planned)
            EasyTierForegroundService.stop(context)
        }.onFailure {
            establishedTunSpecs.remove(name)
            Log.e(TAG, "VPN 服务启动失败", it)
        }
    }

    /**
     * 启动单个网络实例（引擎内），不改变服务开关。
     * 应用层设置（全局 SOCKS5 / DNS）在此统一注入生成的 TOML。
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
        val finalConfig = PortAvoidance.resolve(
            config = config,
            runningConfigs = runningConfigs.values,
            selfName = name,
            socks5Enabled = settings.enableSocks5,
            requestedSocks5Port = settings.socks5Port,
        )
        val configChanged = runningConfigs[name] != null &&
            runningConfigs[name] != finalConfig
        val alreadyRunning = engine.states.value[name] is InstanceState.Running
        if (alreadyRunning && configChanged) {
            engine.stop(name)
        }
        runningNetworks[name] = network
        if (!alreadyRunning || configChanged) {
            engine.start(effective, TomlGenerator.generate(finalConfig))
                .onFailure {
                    runningNetworks.remove(name)
                    Log.e(TAG, "实例启动失败: ${it.message}")
                    return
                }
            runningConfigs[name] = finalConfig
            establishedTunSpecs.remove(name)
            connectivityMonitor.triggerDiscoveryBurst()
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
        runningNetworks.remove(name)
        runningConfigs.remove(name)
        establishedTunSpecs.remove(name)
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
            return Result.failure(
                IllegalStateException(context.getString(R.string.error_no_enabled_networks)),
            )
        }
        _serviceRunning.value = true
        connectivityMonitor.start()
        scope.launch {
            startMutex.withLock {
                if (!_serviceRunning.value) return@withLock
                // 停掉已不在勾选集合里的运行实例
                val keep = enabledNetworks.map { it.config.networkName }.toSet()
                runningConfigs.keys.filter { it !in keep }.forEach { name ->
                    engine.stop(name)
                    runningNetworks.remove(name)
                    runningConfigs.remove(name)
                    establishedTunSpecs.remove(name)
                }
                enabledNetworks.forEach { startNetworkLocked(it) }
                if (!vpnEnabled) {
                    EasyTierForegroundService.start(
                        context,
                        noTunText(enabledNetworks.map { it.config.networkName }),
                    )
                } else {
                    // 已在跑且拿到 IP 的直接补 TUN；尚在等待 DHCP 分配虚拟 IP 期间先挂轻量前台保活，
                    // 防止弱网/开机自启等待 IP 的数秒内被定制系统冻结进程（TUN 建立后会自动关闭）
                    engine.states.value.forEach { (name, state) ->
                        (state as? InstanceState.Running)?.info?.let { reconcileTunLocked(name, it) }
                    }
                    if (establishedTunSpecs.isEmpty() && runningConfigs.isNotEmpty()) {
                        EasyTierForegroundService.start(
                            context,
                            context.getString(R.string.vpn_state_starting),
                        )
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    /** 停止服务（引擎 + TUN 全停）。 */
    fun stopService() {
        _serviceRunning.value = false
        connectivityMonitor.stop()
        scope.launch {
            startMutex.withLock {
                runningNetworks.clear()
                runningConfigs.clear()
                establishedTunSpecs.clear()
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
        connectivityMonitor.stop()
        scope.launch {
            startMutex.withLock {
                runningNetworks.clear()
                runningConfigs.clear()
                establishedTunSpecs.clear()
            }
            EasyTierVpnService.stopTun(context)
            EasyTierForegroundService.stop(context)
            engine.stopAll()
        }
    }

    fun shutdown() {
        _serviceRunning.value = false
        connectivityMonitor.stop()
        scope.launch {
            startMutex.withLock {
                runningNetworks.clear()
                runningConfigs.clear()
                establishedTunSpecs.clear()
            }
            EasyTierVpnService.stopTun(context)
            EasyTierForegroundService.stop(context)
            engine.stopAll()
        }.invokeOnCompletion { scope.cancel() }
    }

    /** 无 TUN 模式的前台通知文案（随应用语言本地化）。 */
    private fun noTunText(names: Collection<String>): String =
        if (names.isEmpty()) {
            context.getString(R.string.fg_state_no_tun)
        } else {
            context.getString(R.string.fg_state_no_tun_networks, names.joinToString(", "))
        }

    companion object {
        private const val TAG = "VpnController"
    }
}
