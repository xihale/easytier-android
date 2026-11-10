package com.easytier.android.core.vpn

import com.easytier.android.data.model.NetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 多实例端口避让的回归测试（纯 JVM，无 Android 依赖）。
 * 重点回归 issue #1：重启/同步配置时不得把自身端口误判为冲突导致端口漂移。
 */
class PortAvoidanceTest {

    @Test
    fun `no other running instances keeps own ports unchanged (issue #1 regression)`() {
        val config = NetworkConfig(
            networkName = "a",
            listenerUrls = listOf("tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011"),
        )
        val final = PortAvoidance.resolve(
            config = config,
            runningConfigs = emptyList(),
            selfName = "a",
            socks5Enabled = true,
            requestedSocks5Port = 1081,
        )
        assertEquals(config.listenerUrls, final.listenerUrls)
        assertEquals(1081, final.socks5Port)
    }

    @Test
    fun `self is excluded even when stale own registration is passed (issue #1 repro)`() {
        // runningConfigs 里仍留有自己的旧记录（重启/编辑同步场景）。
        // 修复前 resolve 会把自己旧端口算进冲突，每次重启 socks5 +1、监听 +10 漂移。
        val own = NetworkConfig(
            networkName = "a",
            listenerUrls = listOf("tcp://0.0.0.0:11010"),
            enableSocks5 = true,
            socks5Port = 1081,
        )
        val final = PortAvoidance.resolve(
            config = own,
            runningConfigs = listOf(own),
            selfName = "a",
            socks5Enabled = true,
            requestedSocks5Port = 1081,
        )
        assertEquals(listOf("tcp://0.0.0.0:11010"), final.listenerUrls)
        assertEquals(1081, final.socks5Port)
    }

    @Test
    fun `second instance avoids first instance socks5 and listener ports`() {
        val first = NetworkConfig(
            networkName = "a",
            listenerUrls = listOf("tcp://0.0.0.0:11010"),
            enableSocks5 = true,
            socks5Port = 1081,
        )
        val second = NetworkConfig(
            networkName = "b",
            listenerUrls = listOf("tcp://0.0.0.0:11010"),
        )
        val final = PortAvoidance.resolve(
            config = second,
            runningConfigs = listOf(first),
            selfName = "b",
            socks5Enabled = true,
            requestedSocks5Port = 1081,
        )
        // 监听端口 +10，SOCKS5 端口 +1
        assertEquals(listOf("tcp://0.0.0.0:11020"), final.listenerUrls)
        assertEquals(1082, final.socks5Port)
    }

    @Test
    fun `only conflicting listener is bumped and later socks5 avoids bumped listener`() {
        val first = NetworkConfig(
            networkName = "a",
            listenerUrls = listOf("tcp://0.0.0.0:11010", "udp://0.0.0.0:11020"),
            enableSocks5 = true,
            socks5Port = 1081,
        )
        val second = NetworkConfig(
            networkName = "b",
            listenerUrls = listOf("tcp://0.0.0.0:11010", "udp://0.0.0.0:11030"),
        )
        val final = PortAvoidance.resolve(
            config = second,
            runningConfigs = listOf(first),
            selfName = "b",
            socks5Enabled = true,
            requestedSocks5Port = 1081,
        )
        // 11010 冲突 → +10 到 11020（被 first 占用）→ 11030；11030 入占用集后，
        // udp 的 11030 再冲突 → 11040（同配置内避让不撞车）
        assertEquals(listOf("tcp://0.0.0.0:11030", "udp://0.0.0.0:11040"), final.listenerUrls)
        // 1081 冲突 → 1082
        assertEquals(1082, final.socks5Port)
    }

    @Test
    fun `socks5 disabled clears socks5 fields`() {
        val final = PortAvoidance.resolve(
            config = NetworkConfig(networkName = "a", enableSocks5 = true, socks5Port = 1081),
            runningConfigs = emptyList(),
            selfName = "a",
            socks5Enabled = false,
            requestedSocks5Port = null,
        )
        assertFalse(final.enableSocks5 == true)
        assertEquals(null, final.socks5Port)
    }

    @Test
    fun `non-numeric listener urls are ignored for conflicts`() {
        val other = NetworkConfig(
            networkName = "a",
            listenerUrls = listOf("tcp://0.0.0.0:notaport"),
        )
        val config = NetworkConfig(networkName = "b", listenerUrls = listOf("tcp://0.0.0.0:11010"))
        val final = PortAvoidance.resolve(
            config = config,
            runningConfigs = listOf(other),
            selfName = "b",
            socks5Enabled = false,
            requestedSocks5Port = null,
        )
        assertEquals(config.listenerUrls, final.listenerUrls)
    }
}
