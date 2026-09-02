package com.easytier.android.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easytier.android.data.update.ReleaseInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 应用设置。 */
data class AppSettings(
    val themeMode: String = "system", // system | light | dark
    val enableVpn: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val enableSocks5: Boolean = false,
    val socks5Port: Int = 1080,
    // Magic DNS 转发普通域名查询的上游服务器，支持 udp:// tcp:// tls://(DoT) https://(DoH)。
    // 空 = 核心内置默认（Android 上为 223.5.5.5 等纯 UDP）
    val dnsServers: List<String> = emptyList(),
    // 检测更新（默认关闭）。off | startup | daily | weekly
    val updateCheckInterval: String = "off",
    val lastUpdateCheckAt: Long = 0L,
    val pendingUpdateVersion: String = "", // 非空 = 有待查看的新版本
    val pendingUpdateUrl: String = "",
    val pendingUpdateNotes: String = "",
)

/**
 * 应用设置仓库（DataStore）。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ENABLE_VPN = booleanPreferencesKey("enable_vpn")
        val AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val SOCKS5_ENABLED = booleanPreferencesKey("enable_socks5")
        val SOCKS5_PORT = intPreferencesKey("socks5_port")
        // 换行分隔保序（DNS 按顺序做故障转移）
        val DNS_SERVERS = stringPreferencesKey("dns_servers")

        val UPDATE_INTERVAL = stringPreferencesKey("update_check_interval")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
        val PENDING_VERSION = stringPreferencesKey("pending_update_version")
        val PENDING_URL = stringPreferencesKey("pending_update_url")
        val PENDING_NOTES = stringPreferencesKey("pending_update_notes")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME] ?: "system",
            enableVpn = p[Keys.ENABLE_VPN] ?: true,
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            enableSocks5 = p[Keys.SOCKS5_ENABLED] ?: false,
            socks5Port = p[Keys.SOCKS5_PORT] ?: 1080,
            dnsServers = p[Keys.DNS_SERVERS].orEmpty().split('\n').filter { it.isNotBlank() },
            updateCheckInterval = p[Keys.UPDATE_INTERVAL] ?: "off",
            lastUpdateCheckAt = p[Keys.LAST_UPDATE_CHECK] ?: 0L,
            pendingUpdateVersion = p[Keys.PENDING_VERSION] ?: "",
            pendingUpdateUrl = p[Keys.PENDING_URL] ?: "",
            pendingUpdateNotes = p[Keys.PENDING_NOTES] ?: "",
        )
    }

    suspend fun setVpnEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.ENABLE_VPN] = enabled }

    suspend fun setThemeMode(mode: String) =
        context.settingsDataStore.edit { it[Keys.THEME] = mode }

    suspend fun setAutoStart(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_START] = enabled }

    suspend fun setSocks5(enabled: Boolean, port: Int) =
        context.settingsDataStore.edit {
            it[Keys.SOCKS5_ENABLED] = enabled
            it[Keys.SOCKS5_PORT] = port.coerceIn(1024..65535)
        }

    suspend fun setDnsServers(servers: List<String>) =
        context.settingsDataStore.edit {
            val joined = servers.filter { it.isNotBlank() }.joinToString("\n")
            if (joined.isEmpty()) it.remove(Keys.DNS_SERVERS) else it[Keys.DNS_SERVERS] = joined
        }

    suspend fun setUpdateInterval(mode: String) =
        context.settingsDataStore.edit { it[Keys.UPDATE_INTERVAL] = mode }

    suspend fun setLastUpdateCheckAt(at: Long) =
        context.settingsDataStore.edit { it[Keys.LAST_UPDATE_CHECK] = at }

    suspend fun setPendingUpdate(info: ReleaseInfo) =
        context.settingsDataStore.edit {
            it[Keys.PENDING_VERSION] = info.version
            it[Keys.PENDING_URL] = info.url
            it[Keys.PENDING_NOTES] = info.notes ?: ""
        }

    suspend fun clearPendingUpdate() = context.settingsDataStore.edit {
        it.remove(Keys.PENDING_VERSION)
        it.remove(Keys.PENDING_URL)
        it.remove(Keys.PENDING_NOTES)
    }
}
