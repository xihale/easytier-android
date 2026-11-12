package com.easytier.android.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 物理底层网络感知与低功耗 Wi-Fi 组播锁调度器。
 *
 * 解决长时间后台运行的两大痛点：
 * 1. **链路切换感知**：通过带 `NET_CAPABILITY_NOT_VPN` 过滤的 [ConnectivityManager.NetworkCallback]
 *    精确监听物理网络（Wi-Fi ↔ 蜂窝切换、重连、IP 变更），忽略自身 TUN 重建噪声，
 *    第一时间唤醒引擎快速收敛窗口刷新 NAT/路由/DHCP。
 * 2. **省电组播脉冲窗口**：过去常驻持有 [WifiManager.MulticastLock] 会迫使 Wi-Fi 芯片禁用
 *    802.11 DTIM 硬件组播过滤，息屏后被局域网 ARP/mDNS/广播风暴频繁唤醒 AP。
 *    现改为仅在存在物理 Wi-Fi 且处于「启动 / 切网 / 回到前台」脉冲窗口（45s）内持锁，
 *    完成局域网 UDP 发现与 UPnP 映射后自动释放，恢复芯片级深度休眠。
 */
internal class NetworkConnectivityMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onPhysicalNetworkChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private data class PhysicalNetState(
        val isWifi: Boolean = false,
        val linkHash: Int = 0,
    )

    private val activeNetworks = ConcurrentHashMap<Network, PhysicalNetState>()

    @Volatile
    private var monitoring = false

    private var debounceJob: Job? = null
    private var multicastBurstJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val lockGuard = Any()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = runCatching { connectivityManager?.getNetworkCapabilities(network) }.getOrNull()
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            activeNetworks[network] = PhysicalNetState(isWifi = isWifi)
            scheduleNotify(triggerMulticastBurst = isWifi)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val prev = activeNetworks[network]
            if (prev == null || prev.isWifi != isWifi) {
                activeNetworks[network] = PhysicalNetState(
                    isWifi = isWifi,
                    linkHash = prev?.linkHash ?: 0,
                )
                scheduleNotify(triggerMulticastBurst = isWifi)
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val addrHash = linkProperties.linkAddresses.map { it.address }.hashCode()
            val prev = activeNetworks[network]
            if (prev == null || prev.linkHash != addrHash) {
                val isWifi = prev?.isWifi ?: false
                activeNetworks[network] = PhysicalNetState(isWifi = isWifi, linkHash = addrHash)
                if (prev != null) {
                    scheduleNotify(triggerMulticastBurst = isWifi)
                }
            }
        }

        override fun onLost(network: Network) {
            activeNetworks.remove(network)
            if (!hasActiveWifi()) {
                releaseMulticastLock()
            }
            scheduleNotify(triggerMulticastBurst = false)
        }
    }

    /** 开启物理网络监听并触发首轮局域网发现窗口（幂等）。 */
    fun start() {
        synchronized(lockGuard) {
            if (monitoring) {
                triggerDiscoveryBurst()
                return
            }
            monitoring = true
            activeNetworks.clear()
            val cm = connectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build()
                runCatching { cm.registerNetworkCallback(request, networkCallback) }
                    .onFailure { Log.w(TAG, "注册物理网络监听失败: ${it.message}") }
            }
        }
        triggerDiscoveryBurst()
    }

    /** 停止物理网络监听并立即释放全部锁与定时协程。 */
    fun stop() {
        synchronized(lockGuard) {
            if (!monitoring) return
            monitoring = false
            debounceJob?.cancel()
            debounceJob = null
            multicastBurstJob?.cancel()
            multicastBurstJob = null
            activeNetworks.clear()
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        }
        releaseMulticastLock()
    }

    /**
     * 按需申请一段短暂的 Wi-Fi 组播窗口（用于局域网对等节点 UDP 广播发现与 UPnP SSDP）。
     * 窗口到期后自动释放，避免常驻破坏 Wi-Fi 省电休眠。
     */
    fun triggerDiscoveryBurst() {
        synchronized(lockGuard) {
            if (!monitoring) return
            if (!hasActiveWifi() && !isSystemWifiConnected()) return
            acquireMulticastLockLocked()
            multicastBurstJob?.cancel()
            multicastBurstJob = scope.launch {
                delay(DISCOVERY_BURST_MS)
                releaseMulticastLock()
            }
        }
    }

    private fun scheduleNotify(triggerMulticastBurst: Boolean) {
        if (!monitoring) return
        if (triggerMulticastBurst) {
            triggerDiscoveryBurst()
        }
        synchronized(lockGuard) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                // 合并同一物理切网过程中 IPv4 / IPv6 双栈连续触发的多次回调
                delay(HANDOFF_DEBOUNCE_MS)
                if (monitoring) {
                    onPhysicalNetworkChanged()
                }
            }
        }
    }

    private fun hasActiveWifi(): Boolean = activeNetworks.values.any { it.isWifi }

    private fun isSystemWifiConnected(): Boolean = runCatching {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    private fun acquireMulticastLockLocked() {
        if (multicastLock?.isHeld == true) return
        val wifi = wifiManager ?: return
        runCatching {
            val lock = (multicastLock ?: wifi.createMulticastLock("easytier_discovery").apply {
                setReferenceCounted(false)
                multicastLock = this
            })
            lock.acquire()
            Log.d(TAG, "已开启 Wi-Fi 组播发现脉冲窗口 (${DISCOVERY_BURST_MS / 1000}s)")
        }.onFailure { Log.w(TAG, "获取 multicast lock 失败: ${it.message}") }
    }

    private fun releaseMulticastLock() {
        synchronized(lockGuard) {
            val lock = multicastLock ?: return
            runCatching {
                if (lock.isHeld) {
                    lock.release()
                    Log.d(TAG, "Wi-Fi 组播发现窗口结束，已释放锁进入省电模式")
                }
            }
        }
    }

    companion object {
        private const val TAG = "NetConnMonitor"
        private const val HANDOFF_DEBOUNCE_MS = 350L
        private const val DISCOVERY_BURST_MS = 45_000L
    }
}
