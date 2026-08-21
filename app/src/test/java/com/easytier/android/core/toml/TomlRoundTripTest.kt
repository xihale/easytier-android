package com.easytier.android.core.toml

import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkingMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TOML 生成/导入的行为验证（纯 JVM，无 Android 依赖）。
 * 重点：应用层设置（全局 SOCKS5）必须真实落入生成的配置。
 */
class TomlRoundTripTest {

    @Test
    fun `basic config contains identity and listeners`() {
        val toml = TomlGenerator.generate(
            NetworkConfig(
                networkName = "mynet",
                networkSecret = "s3cret",
                networkingMethod = NetworkingMethod.PublicServer,
                publicServerUrl = "tcp://public.easytier.cn:11010",
            ),
        )
        assertTrue(toml.contains("instance_name = \"mynet\""))
        assertTrue(toml.contains("network_name = \"mynet\""))
        assertTrue(toml.contains("network_secret = \"s3cret\""))
        assertTrue(toml.contains("tcp://public.easytier.cn:11010"))
        assertTrue(toml.contains("listeners = ["))
        // listeners 必须在第一个 [table] 之前（TOML 顶层键语义）
        assertTrue(toml.indexOf("listeners = [") < toml.indexOf("[network_identity]"))
    }

    @Test
    fun `socks5 enabled emits both flags with port`() {
        val toml = TomlGenerator.generate(
            NetworkConfig(networkName = "n", enableSocks5 = true, socks5Port = 1080),
        )
        assertTrue(toml.contains("enable_socks5 = true"))
        assertTrue(toml.contains("socks5_port = 1080"))
    }

    @Test
    fun `socks5 disabled or absent emits nothing`() {
        val off = TomlGenerator.generate(NetworkConfig(networkName = "n", enableSocks5 = false, socks5Port = 1080))
        assertFalse(off.contains("enable_socks5"))
        assertFalse(off.contains("socks5_port"))
        val absent = TomlGenerator.generate(NetworkConfig(networkName = "n"))
        assertFalse(absent.contains("enable_socks5"))
    }

    @Test
    fun `null flags stay absent (core defaults apply)`() {
        val toml = TomlGenerator.generate(NetworkConfig(networkName = "n"))
        assertFalse(toml.contains("latency_first"))
        assertFalse(toml.contains("no_tun"))
        assertFalse(toml.contains("[flags]"))
    }

    @Test
    fun `manual peers emitted per entry`() {
        val toml = TomlGenerator.generate(
            NetworkConfig(
                networkName = "n",
                networkingMethod = NetworkingMethod.Manual,
                peerUrls = listOf("tcp://10.0.0.1:11010", "udp://10.0.0.2:11010"),
            ),
        )
        assertTrue(toml.contains("uri = \"tcp://10.0.0.1:11010\""))
        assertTrue(toml.contains("uri = \"udp://10.0.0.2:11010\""))
    }

    @Test
    fun `round trip preserves key fields including socks5`() {
        val original = NetworkConfig(
            networkName = "round",
            networkSecret = "pw",
            networkingMethod = NetworkingMethod.Manual,
            peerUrls = listOf("tcp://10.0.0.1:11010"),
            dhcp = false,
            virtualIpv4 = "10.147.0.3",
            networkLength = 24,
            enableSocks5 = true,
            socks5Port = 1088,
            latencyFirst = true,
        )
        val parsed = TomlImporter.parse(TomlGenerator.generate(original)).getOrThrow()
        assertEquals("round", parsed.networkName)
        assertEquals("pw", parsed.networkSecret)
        assertEquals(NetworkingMethod.Manual, parsed.networkingMethod)
        assertEquals(listOf("tcp://10.0.0.1:11010"), parsed.peerUrls)
        assertEquals("10.147.0.3", parsed.virtualIpv4)
        assertEquals(24, parsed.networkLength)
        assertEquals(true, parsed.enableSocks5)
        assertEquals(1088, parsed.socks5Port)
        assertEquals(true, parsed.latencyFirst)
    }

    @Test
    fun `importer reads external toml with socks5 flags`() {
        val parsed = TomlImporter.parse(
            """
            instance_name = "ext"
            dhcp = true
            listeners = ["tcp://0.0.0.0:11010"]
            [network_identity]
            network_name = "ext"
            [flags]
            enable_socks5 = true
            socks5_port = 7890
            """.trimIndent(),
        ).getOrThrow()
        assertEquals("ext", parsed.networkName)
        assertEquals(true, parsed.enableSocks5)
        assertEquals(7890, parsed.socks5Port)
    }

    @Test
    fun `importer rejects malformed toml`() {
        assertTrue(TomlImporter.parse("not [valid toml").isFailure)
    }

    @Test
    fun `special characters escaped in generated toml`() {
        val toml = TomlGenerator.generate(
            NetworkConfig(networkName = "na\"me\\x", networkSecret = "pw\nline"),
        )
        assertTrue(toml.contains("instance_name = \"na\\\"me\\\\x\""))
        assertTrue(toml.contains("network_secret = \"pw\\nline\""))
        // 生成结果必须仍可被解析回来
        assertEquals("na\"me\\x", TomlImporter.parse(toml).getOrThrow().networkName)
    }

    @Test
    fun `diffFromDefaults reports only changed flags`() {
        val diff = TomlGenerator.diffFromDefaults(
            NetworkConfig(networkName = "n", latencyFirst = true, enableSocks5 = true),
        )
        assertEquals(true, diff["latency_first"]?.second)
        assertEquals(true, diff["enable_socks5"]?.second)
        assertNull(diff["no_tun"])
    }
}
