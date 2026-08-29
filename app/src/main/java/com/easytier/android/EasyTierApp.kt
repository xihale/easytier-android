package com.easytier.android

import android.app.Application
import com.easytier.android.core.engine.EasyTierEngine
import com.easytier.android.core.vpn.VpnController
import com.easytier.android.data.store.NetworksRepository
import com.easytier.android.data.store.SettingsRepository
import com.easytier.android.data.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用级依赖容器。 */
class AppContainer(app: Application) {
    val settingsRepository = SettingsRepository(app)
    val networksRepository = NetworksRepository(app)
    val engine = EasyTierEngine()
    val vpnController = VpnController(app, engine, settingsRepository)
    val updateChecker = UpdateChecker(settingsRepository)
}

class EasyTierApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onTerminate() {
        container.vpnController.shutdown()
        super.onTerminate()
    }

    companion object {
        @Volatile private var appInstance: EasyTierApp? = null

        /** 进程级单例访问（ViewModel 等无 Context 入口处使用）。 */
        fun get(): EasyTierApp =
            appInstance ?: error("EasyTierApp 未初始化")

        val instance: EasyTierApp get() = get()
    }

    override fun onCreate() {
        super.onCreate()
        appInstance = this
        // 应用冷启动时，按频率执行一次静默的自动检测更新（失败不上报）
        applicationScope.launch { container.updateChecker.maybeAutoCheck() }
    }
}
