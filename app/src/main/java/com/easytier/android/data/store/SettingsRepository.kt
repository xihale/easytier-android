package com.easytier.android.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 应用设置。 */
data class AppSettings(
    val themeMode: String = "system", // system | light | dark
    val autoStartOnBoot: Boolean = false,
    val autoStartNetworkId: String? = null,
    val startVpnWithNetwork: Boolean = true,
    val enableSocks5: Boolean = false,
    val socks5Port: Int = 1080,
)

/**
 * 应用设置仓库（DataStore）。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val AUTO_START_ID = stringPreferencesKey("auto_start_network_id")
        val VPN_WITH_NETWORK = booleanPreferencesKey("vpn_with_network")
        val SOCKS5_ENABLED = booleanPreferencesKey("socks5_enabled")
        val SOCKS5_PORT = intPreferencesKey("socks5_port")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME] ?: "system",
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            autoStartNetworkId = p[Keys.AUTO_START_ID],
            startVpnWithNetwork = p[Keys.VPN_WITH_NETWORK] ?: true,
            enableSocks5 = p[Keys.SOCKS5_ENABLED] ?: false,
            socks5Port = p[Keys.SOCKS5_PORT] ?: 1080,
        )
    }

    suspend fun setThemeMode(mode: String) =
        context.settingsDataStore.edit { it[Keys.THEME] = mode }

    suspend fun setAutoStart(enabled: Boolean, networkId: String?) =
        context.settingsDataStore.edit {
            it[Keys.AUTO_START] = enabled
            if (networkId != null) it[Keys.AUTO_START_ID] = networkId
        }

    suspend fun setVpnWithNetwork(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.VPN_WITH_NETWORK] = enabled }

    suspend fun setSocks5(enabled: Boolean, port: Int) =
        context.settingsDataStore.edit {
            it[Keys.SOCKS5_ENABLED] = enabled
            it[Keys.SOCKS5_PORT] = port.coerceIn(1024..65535)
        }
}
