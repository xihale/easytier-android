package com.easytier.android.core.engine

import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.NetworkInstanceRunningInfoMap
import com.easytier.android.data.model.RuntimeJson
import com.easytier.android.data.model.SavedNetwork
import com.easytier.jni.EasyTierJNI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * EasyTier 引擎：管理网络实例生命周期，轮询运行状态。
 *
 * JNI 调用全部在 IO 调度器上执行；状态以 StateFlow 暴露给 UI。
 */
class EasyTierEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _states = MutableStateFlow<Map<String, InstanceState>>(emptyMap())
    val states: StateFlow<Map<String, InstanceState>> = _states.asStateFlow()

    private val _events = MutableStateFlow<List<EngineEvent>>(emptyList())
    val events: StateFlow<List<EngineEvent>> = _events.asStateFlow()

    private var pollJob: Job? = null
    private val runningInstances = mutableSetOf<String>()

    /** 启动网络实例。 */
    suspend fun start(network: SavedNetwork, toml: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val name = network.config.networkName
                _states.value = _states.value + (name to InstanceState.Starting)
                emitEvent("启动实例 $name")

                val rc = EasyTierJNI.runNetworkInstance(toml)
                if (rc != 0) {
                    val err = EasyTierJNI.getLastError() ?: "unknown error (rc=$rc)"
                    _states.value = _states.value + (name to InstanceState.Error(err))
                    emitEvent("实例 $name 启动失败: $err")
                    throw IllegalStateException(err)
                }
                runningInstances.add(name)
                _states.value = _states.value + (name to InstanceState.Running(NetworkInstanceRunningInfo()))
                emitEvent("实例 $name 已启动")
                startPolling()
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

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                runCatching { pollOnce() }.onFailure {
                    emitEvent("状态轮询失败: ${it.message}")
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollOnce() {
        val raw = EasyTierJNI.collectNetworkInfos(MAX_INSTANCES) ?: return
        val parsed = runCatching {
            RuntimeJson.json.decodeFromString<NetworkInstanceRunningInfoMap>(raw)
        }.onFailure {
            android.util.Log.w("EasyTierEngine", "运行信息解析失败: ${it.message}")
        }.getOrNull() ?: return

        val newStates = _states.value.toMutableMap()
        for ((name, info) in parsed.map) {
            if (name !in runningInstances) continue
            newStates[name] = if (info.running) {
                InstanceState.Running(info)
            } else {
                InstanceState.Error(info.errorMsg ?: "实例未在运行")
            }
        }
        _states.value = newStates
    }

    private fun emitEvent(message: String) {
        _events.value = (_events.value + EngineEvent(message = message)).takeLast(MAX_EVENTS)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_INSTANCES = 16
        private const val MAX_EVENTS = 200
    }
}
