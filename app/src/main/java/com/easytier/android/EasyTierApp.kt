package com.easytier.android

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import com.easytier.android.core.engine.EasyTierEngine
import com.easytier.android.core.vpn.VpnController
import com.easytier.android.data.store.NetworksRepository
import com.easytier.android.data.store.SettingsRepository
import com.easytier.android.data.update.UpdateChecker
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用级依赖容器。 */
class AppContainer(app: Application) {
    /** 非 UI 层（ViewModel 报错文案等）取本地化字符串用。 */
    val appContext: android.content.Context = app.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val networksRepository = NetworksRepository(appContext)
    val engine = EasyTierEngine(appContext)
    val vpnController = VpnController(appContext, engine, settingsRepository)
    val updateChecker = UpdateChecker(settingsRepository)
}

class EasyTierApp : Application() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "应用级后台协程未捕获异常", throwable)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    val container: AppContainer by lazy { AppContainer(this) }

    private val startedActivityCount = AtomicInteger(0)

    @Volatile
    private var lastObservedNightMask: Int = -1

    override fun onTerminate() {
        container.vpnController.shutdown()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "EasyTierApp"

        @Volatile
        private var appInstance: EasyTierApp? = null

        /** 进程级单例访问（ViewModel 等无 Context 入口处使用）。 */
        fun get(): EasyTierApp =
            appInstance ?: error("EasyTierApp 未初始化")

        val instance: EasyTierApp get() = get()
    }

    override fun onCreate() {
        super.onCreate()
        appInstance = this
        lastObservedNightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        registerForegroundVisibilityCallbacks()

        // 异步预热设置快照与静默自动更新检查，不阻塞冷启动主线程
        applicationScope.launch(Dispatchers.IO) {
            runCatching { container.settingsRepository.settings.first() }
            runCatching { container.updateChecker.maybeAutoCheck() }
        }
        // 后台线程执行桌面图标深浅色兜底同步，避免主线程同步 PackageManager IPC
        applicationScope.launch(Dispatchers.IO) {
            runCatching { LauncherIconRefresher.syncIfNeeded(this@EasyTierApp) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nightMask = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMask != lastObservedNightMask) {
            lastObservedNightMask = nightMask
            applicationScope.launch(Dispatchers.IO) {
                runCatching { LauncherIconRefresher.syncIfNeeded(this@EasyTierApp) }
            }
        }
    }

    /**
     * 精准追踪 Activity 可见生命周期（按 Home 键回桌面或息屏锁屏均会触发 onActivityStopped）：
     * - 进入可见态：切换引擎至 2s 前台轮询并立即刷新一次状态，触发 Wi-Fi 发现脉冲；
     * - 退入后台/息屏：切换引擎至 30s~60s 拓扑级低功耗待机巡检。
     */
    private fun registerForegroundVisibilityCallbacks() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (startedActivityCount.getAndIncrement() == 0) {
                        container.engine.setForeground(true)
                        container.vpnController.onAppForeground()
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    if (startedActivityCount.decrementAndGet().coerceAtLeast(0) == 0) {
                        startedActivityCount.set(0)
                        container.engine.setForeground(false)
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
