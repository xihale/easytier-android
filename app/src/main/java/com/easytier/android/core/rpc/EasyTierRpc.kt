package com.easytier.android.core.rpc

import com.easytier.jni.EasyTierJNI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * EasyTier 管理 RPC 客户端（经 JNI callJsonRpc，protobuf JSON）。
 *
 * 服务名常量与 easytier-core/src/management 对应；payload 需带 instance selector。
 */
object EasyTierRpc {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val SERVICE_PEER_MANAGE = "api.instance.PeerManageRpcService"
    const val SERVICE_ACL = "api.instance.AclManageRpcService"
    const val SERVICE_PORT_FORWARD = "api.instance.PortForwardManageRpcService"
    const val SERVICE_TCP_PROXY = "api.instance.TcpProxyRpcService"
    const val SERVICE_STATS = "api.instance.StatsRpcService"
    const val SERVICE_LOGGER = "api.logger.LoggerRpcService"

    /** 构造带 instance selector 的请求体。 */
    fun selector(instanceId: String): JsonObject = buildJsonObject {
        put("instanceId", instanceId)
    }

    suspend fun call(
        serviceName: String,
        methodName: String,
        payload: JsonObject,
        domainName: String? = null,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = EasyTierJNI.callJsonRpc(serviceName, methodName, domainName, payload.toString())
                ?: throw IllegalStateException(EasyTierJNI.getLastError() ?: "RPC 无响应")
            json.parseToJsonElement(resp) as? JsonObject
                ?: throw IllegalStateException("RPC 响应非 JSON 对象: $resp")
        }
    }
}
