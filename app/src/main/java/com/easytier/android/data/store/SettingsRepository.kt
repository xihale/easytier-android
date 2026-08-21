package com.easytier.android.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 应用设置。 */
data class AppSettings(
    val themeMode: String = "system", // system | light | dark
    val language: String = "system",
    val logLevel: String = "info",
    val autoStartOnBoot: Boolean = false,
    val autoStartNetworkId: String? = null,
    val startVpnWithNetwork: Boolean = true,
)

/**
 * 应用设置仓库（DataStore）。
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val AUTO_START_ID = stringPreferencesKey("auto_start_network_id")
        val VPN_WITH_NETWORK = booleanPreferencesKey("vpn_with_network")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME] ?: "system",
            language = p[Keys.LANGUAGE] ?: "system",
            logLevel = p[Keys.LOG_LEVEL] ?: "info",
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            autoStartNetworkId = p[Keys.AUTO_START_ID],
            startVpnWithNetwork = p[Keys.VPN_WITH_NETWORK] ?: true,
        )
    }

    suspend fun setThemeMode(mode: String) =
        context.settingsDataStore.edit { it[Keys.THEME] = mode }

    suspend fun setLanguage(lang: String) =
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = lang }

    suspend fun setLogLevel(level: String) =
        context.settingsDataStore.edit { it[Keys.LOG_LEVEL] = level }

    suspend fun setAutoStart(enabled: Boolean, networkId: String?) =
        context.settingsDataStore.edit {
            it[Keys.AUTO_START] = enabled
            if (networkId != null) it[Keys.AUTO_START_ID] = networkId
        }

    suspend fun setVpnWithNetwork(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.VPN_WITH_NETWORK] = enabled }
}
