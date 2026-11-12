package com.easytier.android.core.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.easytier.android.R
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.NetworkInstanceRunningInfoMap
import com.easytier.android.data.model.RuntimeJson
import com.easytier.android.data.model.SavedNetwork
import com.easytier.jni.EasyTierJNI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 单实例运行状态。 */
sealed interface InstanceState {
    data object Stopped : InstanceState
    data object Starting : InstanceState
    data class Running(val info: NetworkInstanceRunningInfo) : InstanceState
    data class Error(val message: String) : InstanceState
}

/** 引擎事件日志条目。 */
data class EngineEvent(
    val time: Long = System.currentTimeMillis(),
    val message: String,
)

/**
 * EasyTier 引擎：管理网络实例生命周期，自适应低功耗轮询运行状态。
 *
 * 轮询节拍随生命周期与收敛阶段动态伸缩：
 * - 启动/切网收敛期（尚未拿到虚拟 IP）：500ms 快速收敛，实现亚秒级 TUN 建立；
 * - UI 前台可见期：2000ms 刷新实时吞吐与延迟；
 * - 后台/息屏待机期：自动退避至 30s → 60s 低频巡检，且仅在拓扑/IP/运行态变化时发射状态，
 *   避免流量计数器跳动在后台持续唤醒下游收集器与 CPU。
 */
class EasyTierEngine(private val appContext: Context? = null) {

    /** 展示给用户的兜底错误文案（网络卡副标题/状态页提示），随应用语言本地化。 */
    private val notRunningMessage: String
        get() = appContext?.getString(R.string.engine_not_running) ?: "Instance not running"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _states = MutableStateFlow<Map<String, InstanceState>>(emptyMap())
    val states: StateFlow<Map<String, InstanceState>> = _states.asStateFlow()

    private val eventsBuffer = ArrayDeque<EngineEvent>(MAX_EVENTS)
    private val _events = MutableStateFlow<List<EngineEvent>>(emptyList())
    val events: StateFlow<List<EngineEvent>> = _events.asStateFlow()

    private var pollJob: Job? = null
    private val runningInstances = ConcurrentHashMap.newKeySet<String>()
    private val pollWakeSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var isForeground: Boolean = false

    @Volatile
    private var fastBootstrapUntilMs: Long = 0L

    @Volatile
    private var idleBackoffSteps: Int = 0

    /**
     * 更新应用前后台可见性（由 Application ActivityLifecycleCallbacks 驱动）。
     * 切回前台且存在运行实例时，立即唤醒一次无延迟采样。
     */
    fun setForeground(foreground: Boolean) {
        val prev = isForeground
        isForeground = foreground
        if (foreground && !prev) {
            idleBackoffSteps = 0
            if (runningInstances.isNotEmpty()) {
                pollWakeSignal.trySend(Unit)
            }
        }
    }

    /** 物理底层网络切换（Wi-Fi ↔ 蜂窝 / 重连）时唤醒快速巡检，及时感知新虚拟 IP 或子网路由变化。 */
    fun onPhysicalNetworkChanged() {
        if (runningInstances.isEmpty()) return
        idleBackoffSteps = 0
        markFastBootstrap(NETWORK_HANDOFF_WINDOW_MS)
        pollWakeSignal.trySend(Unit)
    }

    /** 启动网络实例。 */
    suspend fun start(network: SavedNetwork, toml: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val name = network.config.networkName
            runCatching {
                _states.value = _states.value + (name to InstanceState.Starting)
                emitEvent("启动实例 $name")

                // 清理核心中遗留的孤儿实例：此前启动失败的实例仍注册在核心里，
                // 不清理的话重试会报 "instance already exists"。只保留我们仍在管理的实例。
                runCatching {
                    if (runningInstances.isEmpty()) {
                        EasyTierJNI.stopAllInstances()
                    } else {
                        EasyTierJNI.retainNetworkInstance(runningInstances.toTypedArray())
                    }
                }

                val rc = EasyTierJNI.runNetworkInstance(toml)
                if (rc != 0) {
                    val err = EasyTierJNI.getLastError() ?: "unknown error (rc=$rc)"
                    throw IllegalStateException(err)
                }
                runningInstances.add(name)
                _states.value = _states.value + (name to InstanceState.Running(NetworkInstanceRunningInfo()))
                emitEvent("实例 $name 已启动")
                idleBackoffSteps = 0
                markFastBootstrap(FAST_BOOTSTRAP_WINDOW_MS)
                startPolling()
                pollWakeSignal.trySend(Unit)
                Unit
            }.onFailure { err ->
                val msg = err.message ?: "start failed"
                _states.value = _states.value + (name to InstanceState.Error(msg))
                emitEvent("实例 $name 启动失败: $msg")
            }
        }
    }

    /** 停止指定实例（保留其他运行中实例）。 */
    suspend fun stop(networkName: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val keep = runningInstances.toMutableSet().apply { remove(networkName) }
            runCatching {
                if (keep.isEmpty()) {
                    EasyTierJNI.stopAllInstances()
                } else {
                    EasyTierJNI.retainNetworkInstance(keep.toTypedArray())
                }
            }.onSuccess {
                runningInstances.remove(networkName)
                _states.value = _states.value + (networkName to InstanceState.Stopped)
                emitEvent("实例 $networkName 已停止")
                if (runningInstances.isEmpty()) stopPolling()
            }.onFailure {
                emitEvent("停止 $networkName 失败: ${it.message}")
            }
        }
    }

    /** 停止全部。 */
    suspend fun stopAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { EasyTierJNI.stopAllInstances() }.onSuccess {
                runningInstances.clear()
                _states.value = _states.value.mapValues { InstanceState.Stopped as InstanceState }
                emitEvent("全部实例已停止")
                stopPolling()
            }
        }
    }

    private fun markFastBootstrap(durationMs: Long) {
        val target = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L) + durationMs
        if (target > fastBootstrapUntilMs) {
            fastBootstrapUntilMs = target
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive && runningInstances.isNotEmpty()) {
                val intervalMs = currentPollIntervalMs()
                withTimeoutOrNull(intervalMs) {
                    pollWakeSignal.receive()
                }
                if (!isActive || runningInstances.isEmpty()) break
                runCatching { pollOnce() }.onFailure {
                    emitEvent("状态轮询失败: ${it.message}")
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        idleBackoffSteps = 0
        fastBootstrapUntilMs = 0L
    }

    private fun currentPollIntervalMs(): Long {
        val nowElapsed = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        val inFastWindow = nowElapsed in 1 until fastBootstrapUntilMs
        val currentMap = _states.value
        val allSettled = runningInstances.all { name ->
            when (val s = currentMap[name]) {
                is InstanceState.Running -> s.info.myNodeInfo?.virtualIpv4 != null
                is InstanceState.Error -> true
                else -> false
            }
        }
        return computePollIntervalMs(
            isForeground = isForeground,
            inFastWindow = inFastWindow,
            allSettled = allSettled,
            idleBackoffSteps = idleBackoffSteps,
        )
    }

    private fun pollOnce() {
        if (runningInstances.isEmpty()) return
        val raw = EasyTierJNI.collectNetworkInfos(MAX_INSTANCES) ?: return
        val parsed = runCatching {
            RuntimeJson.json.decodeFromString<NetworkInstanceRunningInfoMap>(raw)
        }.onFailure {
            Log.w(TAG, "运行信息解析失败: ${it.message}")
        }.getOrNull() ?: return

        val oldStates = _states.value
        val newStates = oldStates.toMutableMap()
        var topologyChanged = false

        for ((name, info) in parsed.map) {
            if (name !in runningInstances) continue
            val nextState = if (info.running) {
                InstanceState.Running(info)
            } else {
                InstanceState.Error(info.errorMsg ?: notRunningMessage)
            }
            val prevState = oldStates[name]
            if (!isSameTopologyState(prevState, nextState)) {
                topologyChanged = true
            }
            newStates[name] = nextState
        }

        if (isForeground) {
            idleBackoffSteps = 0
            if (newStates != oldStates) {
                _states.value = newStates
            }
        } else {
            // 后台待机态：仅在运行状态、虚拟 IP 或路由网段等核心拓扑真正变化时才发射 StateFlow，
            // 屏蔽纯 rxBytes/txBytes/latency 计数器跳动引起的无效锁竞争与重组唤醒。
            if (topologyChanged) {
                idleBackoffSteps = 0
                _states.value = newStates
            } else {
                idleBackoffSteps = (idleBackoffSteps + 1).coerceAtMost(MAX_IDLE_BACKOFF_STEPS)
            }
        }
    }

    private fun emitEvent(message: String) {
        val snapshot = synchronized(eventsBuffer) {
            if (eventsBuffer.size >= MAX_EVENTS) {
                eventsBuffer.removeFirst()
            }
            eventsBuffer.addLast(EngineEvent(message = message))
            eventsBuffer.toList()
        }
        _events.value = snapshot
    }

    companion object {
        private const val TAG = "EasyTierEngine"
        internal const val FAST_POLL_INTERVAL_MS = 500L
        internal const val FOREGROUND_POLL_INTERVAL_MS = 2_000L
        internal const val BG_CONVERGING_POLL_INTERVAL_MS = 5_000L
        internal const val BG_IDLE_POLL_INTERVAL_MS = 30_000L
        internal const val BG_DEEP_IDLE_POLL_INTERVAL_MS = 60_000L

        private const val FAST_BOOTSTRAP_WINDOW_MS = 12_000L
        private const val NETWORK_HANDOFF_WINDOW_MS = 8_000L
        private const val MAX_IDLE_BACKOFF_STEPS = 4
        private const val MAX_INSTANCES = 16
        private const val MAX_EVENTS = 200

        /** 纯函数：计算下一拍轮询间隔（ms）。 */
        internal fun computePollIntervalMs(
            isForeground: Boolean,
            inFastWindow: Boolean,
            allSettled: Boolean,
            idleBackoffSteps: Int,
        ): Long = when {
            inFastWindow && !allSettled -> FAST_POLL_INTERVAL_MS
            isForeground -> FOREGROUND_POLL_INTERVAL_MS
            !allSettled -> BG_CONVERGING_POLL_INTERVAL_MS
            idleBackoffSteps >= 2 -> BG_DEEP_IDLE_POLL_INTERVAL_MS
            else -> BG_IDLE_POLL_INTERVAL_MS
        }

        /**
         * 判断两个实例状态在「核心拓扑与连接可用性」层面是否一致（忽略字节计数与微秒延迟抖动）。
         */
        internal fun isSameTopologyState(prev: InstanceState?, next: InstanceState?): Boolean {
            if (prev === next) return true
            if (prev == null || next == null || prev::class != next::class) return false
            return when (prev) {
                is InstanceState.Stopped, is InstanceState.Starting -> true
                is InstanceState.Error -> prev.message == (next as InstanceState.Error).message
                is InstanceState.Running -> {
                    val a = prev.info
                    val b = (next as InstanceState.Running).info
                    a.running == b.running &&
                        a.errorMsg == b.errorMsg &&
                        a.devName == b.devName &&
                        a.myNodeInfo?.virtualIpv4 == b.myNodeInfo?.virtualIpv4 &&
                        a.routes.size == b.routes.size &&
                        hasSameRouteTopology(a, b)
                }
            }
        }

        private fun hasSameRouteTopology(
            a: NetworkInstanceRunningInfo,
            b: NetworkInstanceRunningInfo,
        ): Boolean {
            for (i in a.routes.indices) {
                val ra = a.routes[i]
                val rb = b.routes[i]
                if (ra.peerId != rb.peerId ||
                    ra.ipv4Addr != rb.ipv4Addr ||
                    ra.nextHopPeerId != rb.nextHopPeerId ||
                    ra.cost != rb.cost ||
                    ra.proxyCidrs != rb.proxyCidrs
                ) {
                    return false
                }
            }
            return true
        }
    }
}
