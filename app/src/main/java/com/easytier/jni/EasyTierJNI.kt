@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.easytier.jni

/** 配置服务器事件回调（远程配置应用/删除事件）。 */
fun interface ConfigServerEventCallback {
    fun onEvent(eventJson: String)
}

/**
 * EasyTier JNI 接口。
 *
 * 与 easytier-contrib/easytier-android-jni 中的 Rust 实现（libeasytier_android_jni.so）一一对应。
 * 符号绑定 com.easytier.jni.EasyTierJNI，勿改包名。
 */
object EasyTierJNI {
    init {
        System.loadLibrary("easytier_android_jni")
    }

    /** 设置 TUN 文件描述符。返回 0 成功。 */
    @JvmStatic external fun setTunFd(instanceName: String, fd: Int): Int

    /** 解析 TOML 配置字符串，返回 0 成功。 */
    @JvmStatic external fun parseConfig(config: String): Int

    /** 运行网络实例（TOML 配置），返回 0 成功。 */
    @JvmStatic external fun runNetworkInstance(config: String): Int

    /** 启动配置服务器客户端，返回 0 成功。 */
    @JvmStatic
    external fun startConfigServerClient(
        url: String,
        hostname: String?,
        machineId: String,
        secureMode: Boolean,
        callback: ConfigServerEventCallback?,
    ): Int

    /** 停止配置服务器客户端。 */
    @JvmStatic external fun stopConfigServerClient(): Int

    /** 配置服务器客户端是否已连接。 */
    @JvmStatic external fun isConfigServerClientConnected(): Boolean

    /** 保留指定实例，停止其他实例。null/空数组停止所有。 */
    @JvmStatic external fun retainNetworkInstance(instanceNames: Array<String>?): Int

    /** 收集网络运行信息（JSON，key 为实例名）。 */
    @JvmStatic external fun collectNetworkInfos(maxLength: Int): String?

    /** 列出运行中实例（JSON：instance name -> instance id）。 */
    @JvmStatic external fun listInstances(maxLength: Int): String?

    /**
     * 调用 EasyTier RPC 方法（protobuf JSON）。
     * 不支持 api.manage.WebClientService；实例管理用专用 API。
     * payloadJson 需包含目标 RPC 所需的 instance selector。
     */
    @JvmStatic
    external fun callJsonRpc(
        serviceName: String,
        methodName: String,
        domainName: String?,
        payloadJson: String,
    ): String?

    @JvmStatic
    fun callJsonRpc(serviceName: String, methodName: String, payloadJson: String): String? =
        callJsonRpc(serviceName, methodName, null, payloadJson)

    /** 最近一次错误信息。 */
    @JvmStatic external fun getLastError(): String?

    /** 停止全部实例。 */
    @JvmStatic fun stopAllInstances(): Int = retainNetworkInstance(null)

    /** 仅保留单个实例。 */
    @JvmStatic fun retainSingleInstance(instanceName: String): Int =
        retainNetworkInstance(arrayOf(instanceName))
}
