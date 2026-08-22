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
    fun `socks5 enabled emits top-level socks5_proxy before tables`() {
        val toml = TomlGenerator.generate(
            NetworkConfig(networkName = "n", enableSocks5 = true, socks5Port = 1080),
        )
        // 核心只认顶层键 socks5_proxy（toml.rs Config），不是 [flags]
        assertTrue(toml.contains("socks5_proxy = \"socks5://0.0.0.0:1080\""))
        assertFalse(toml.contains("enable_socks5"))
        assertFalse(toml.contains("socks5_port"))
        // 顶层键必须在第一个 [table] 头之前
        assertTrue(toml.indexOf("socks5_proxy") < toml.indexOf("[network_identity]"))
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
    fun `importer keeps public-ip manual peer as manual (not public server)`() {
        val parsed = TomlImporter.parse(
            """
            instance_name = "vps"
            [network_identity]
            network_name = "vps"
            [[peer]]
            uri = "tcp://203.0.113.10:11010"
            """.trimIndent(),
        ).getOrThrow()
        assertEquals(NetworkingMethod.Manual, parsed.networkingMethod)
        assertEquals(listOf("tcp://203.0.113.10:11010"), parsed.peerUrls)
        assertNull(parsed.publicServerUrl)
    }

    @Test
    fun `importer keeps hostname manual peer as manual`() {
        val parsed = TomlImporter.parse(
            """
            [network_identity]
            network_name = "n"
            [[peer]]
            uri = "tcp://my-vps.example.com:11010"
            """.trimIndent(),
        ).getOrThrow()
        assertEquals(NetworkingMethod.Manual, parsed.networkingMethod)
        assertEquals(listOf("tcp://my-vps.example.com:11010"), parsed.peerUrls)
    }

    @Test
    fun `official public server still inferred as public server mode`() {
        val original = NetworkConfig(
            networkingMethod = NetworkingMethod.PublicServer,
            publicServerUrl = "tcp://public.easytier.cn:11010",
        )
        val parsed = TomlImporter.parse(TomlGenerator.generate(original)).getOrThrow()
        assertEquals(NetworkingMethod.PublicServer, parsed.networkingMethod)
        assertEquals("tcp://public.easytier.cn:11010", parsed.publicServerUrl)
    }

    @Test
    fun `multiple peers never inferred as public server even if one is official`() {
        val parsed = TomlImporter.parse(
            """
            [network_identity]
            network_name = "n"
            [[peer]]
            uri = "tcp://public.easytier.cn:11010"
            [[peer]]
            uri = "tcp://203.0.113.10:11010"
            """.trimIndent(),
        ).getOrThrow()
        assertEquals(NetworkingMethod.Manual, parsed.networkingMethod)
    }

    @Test
    fun `public server host matching is case-insensitive`() {
        val parsed = TomlImporter.parse(
            """
            [network_identity]
            network_name = "n"
            [[peer]]
            uri = "tcp://Public.EasyTier.CN:11010"
            """.trimIndent(),
        ).getOrThrow()
        assertEquals(NetworkingMethod.PublicServer, parsed.networkingMethod)
    }

    @Test
    fun `generate after import is idempotent for rich config`() {
        val original = NetworkConfig(
            networkName = "rich",
            networkSecret = "pw",
            networkingMethod = NetworkingMethod.Manual,
            peerUrls = listOf("tcp://203.0.113.10:11010", "udp://my-vps.example.com:11010"),
            dhcp = false,
            virtualIpv4 = "10.147.0.3",
            networkLength = 24,
            hostname = "phone",
            listenerUrls = listOf("tcp://0.0.0.0:11010"),
            proxyCidrs = listOf("10.0.0.0/24"),
            latencyFirst = true,
            enableKcpProxy = true,
        )
        val once = TomlGenerator.generate(original)
        val first = TomlImporter.parse(once).getOrThrow()
        // 语义幂等：导入→再生成→再导入后除身份外不变。
        // 字节级对比不可行：instanceId 为 null 时生成器每次随机出新 UUID，
        // 而导入器按设计丢弃身份（避免两端同 peer_id 互踢）。
        val twice = TomlImporter.parse(TomlGenerator.generate(first)).getOrThrow()
        assertEquals(first, twice)
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
    fun `importer reads external toml with socks5 proxy`() {
        val parsed = TomlImporter.parse(
            """
            instance_name = "ext"
            dhcp = true
            listeners = ["tcp://0.0.0.0:11010"]
            socks5_proxy = "socks5://0.0.0.0:7890"
            [network_identity]
            network_name = "ext"
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
    fun `acl section survives import and regeneration`() {
        val acl = "[acl]\nsomething = \"kept\""
        val toml = """
            instance_name = "ext"
            dhcp = true
            [network_identity]
            network_name = "ext"
            $acl
        """.trimIndent()
        val parsed = TomlImporter.parse(toml).getOrThrow()
        assertEquals(acl, parsed.acl)
        // 生成器把 ACL 原样回写到输出末尾
        val regenerated = TomlGenerator.generate(parsed)
        assertTrue(regenerated.contains("[acl]"))
        assertTrue(regenerated.contains("something = \"kept\""))
    }

    @Test
    fun `exit nodes emitted top-level and survive round trip`() {
        val toml = TomlGenerator.generate(
            NetworkConfig(networkName = "n", exitNodes = listOf("10.126.126.1", "10.126.126.2")),
        )
        assertTrue(toml.contains("exit_nodes = [\"10.126.126.1\", \"10.126.126.2\"]"))
        assertTrue(toml.indexOf("exit_nodes") < toml.indexOf("[network_identity]"))
        val parsed = TomlImporter.parse(toml).getOrThrow()
        assertEquals(listOf("10.126.126.1", "10.126.126.2"), parsed.exitNodes)
        val empty = TomlGenerator.generate(NetworkConfig(networkName = "n"))
        assertFalse(empty.contains("exit_nodes"))
    }

    @Test
    fun `export without instance id omits identity and still parses`() {
        val config = NetworkConfig(networkName = "share", instanceId = "stable-uuid")
        val shared = TomlGenerator.generate(config, includeInstanceId = false)
        assertFalse(shared.contains("instance_id"))
        // 引擎启动路径默认仍带身份
        assertTrue(TomlGenerator.generate(config).contains("instance_id = \"stable-uuid\""))
        // 不带身份的导出仍可正常导入（导入方本就丢弃身份）
        assertEquals("share", TomlImporter.parse(shared).getOrThrow().networkName)
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
