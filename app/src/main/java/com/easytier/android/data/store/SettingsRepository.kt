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
    val enableVpn: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val enableSocks5: Boolean = false,
    val socks5Port: Int = 1080,
)

/**
 * 应用设置仓库（DataStore）。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ENABLE_VPN = booleanPreferencesKey("enable_vpn")
        val AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val SOCKS5_ENABLED = booleanPreferencesKey("socks5_enabled")
        val SOCKS5_PORT = intPreferencesKey("socks5_port")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME] ?: "system",
            enableVpn = p[Keys.ENABLE_VPN] ?: true,
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            enableSocks5 = p[Keys.SOCKS5_ENABLED] ?: false,
            socks5Port = p[Keys.SOCKS5_PORT] ?: 1080,
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
}
