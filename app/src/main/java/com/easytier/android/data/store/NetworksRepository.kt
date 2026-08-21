package com.easytier.android.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.SavedNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.networksDataStore by preferencesDataStore(name = "networks")

/**
 * 已保存网络配置仓库（DataStore 持久化）。
 * 每个 SavedNetwork 以 instance_id 为 key 存储 JSON。
 */
class NetworksRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    val networks: Flow<List<SavedNetwork>> = context.networksDataStore.data.map { prefs ->
        prefs.asMap().values.mapNotNull { v ->
            (v as? String)?.let { runCatching { json.decodeFromString<SavedNetwork>(it) }.getOrNull() }
        }.sortedBy { it.createdAt }
    }

    suspend fun save(network: SavedNetwork) {
        context.networksDataStore.edit { prefs ->
            prefs[stringPreferencesKey(network.id)] = json.encodeToString(network)
        }
    }

    suspend fun delete(id: String) {
        context.networksDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(id))
        }
    }

    suspend fun get(id: String): SavedNetwork? =
        context.networksDataStore.data.first()[stringPreferencesKey(id)]
            ?.let { runCatching { json.decodeFromString<SavedNetwork>(it) }.getOrNull() }

    companion object {
        fun newNetwork(config: NetworkConfig = NetworkConfig()): SavedNetwork =
            SavedNetwork(
                id = config.instanceId ?: java.util.UUID.randomUUID().toString(),
                config = config.copy(instanceId = config.instanceId ?: java.util.UUID.randomUUID().toString()),
            )
    }
}
