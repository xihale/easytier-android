package com.easytier.android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.ui.nav.TopDestination
import com.easytier.android.ui.screens.editor.NetworkEditorScreen
import com.easytier.android.ui.screens.networks.NetworksScreen
import com.easytier.android.ui.screens.settings.ClashHelpScreen
import com.easytier.android.ui.screens.settings.DnsSettingsScreen
import com.easytier.android.ui.screens.settings.SettingsScreen
import com.easytier.android.ui.screens.status.StatusScreen
import com.easytier.android.ui.theme.EasyTierTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** 授权框结果：通过则自动启动服务（磁贴/失败通知转来的场景）。 */
    private val vpnGrantLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startServiceAfterVpnGrant()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleVpnIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val container = (application as EasyTierApp).container
        // 初始设置快照（内存快照或轻量缓存）：避免主线程阻塞 DataStore I/O，首帧不闪主题
        val initialSettings = container.settingsRepository.initialSettings()
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = initialSettings)
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            // onCreate 里的 enableEdgeToEdge 只按系统深浅色定图标色；
            // 应用内切主题时需按已解析的 dark 重设，否则浅色主题+系统深色会白图标叠浅底
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            EasyTierTheme(darkTheme = dark) {
                EasyTierAppNavHost(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVpnIntent(intent)
    }

    /**
     * 磁贴点击 / 失败通知无法弹系统 VPN 授权框，转由应用内申请；
     * 已授权或授权通过后自动启动服务，补上「被其他 VPN 抢占后从磁贴重开」的场景。
     */
    private fun handleVpnIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_VPN_AND_START, false) != true) return
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnGrantLauncher.launch(prepare) else startServiceAfterVpnGrant()
    }

    private fun startServiceAfterVpnGrant() {
        lifecycleScope.launch {
            val container = (application as EasyTierApp).container
            val enabled = container.networksRepository.networks.first().filter { it.enabled }
            container.vpnController.startService(enabled)
        }
    }

    companion object {
        /** 携带此 extra 打开应用：申请 VPN 授权，通过后自动启动服务。 */
        const val EXTRA_REQUEST_VPN_AND_START = "easytier.extra.REQUEST_VPN_AND_START"
    }
}

@Composable
private fun EasyTierAppNavHost(container: AppContainer) {
    val navController: NavHostController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // 顶层目的地（带底部导航）
    val topLevelRoutes = setOf("networks", "status", "settings")

    Scaffold(
        bottomBar = {
            // 进出编辑页时底栏滑入/滑出而非瞬间消失，避免内容区跳变带来的顿挫感
            AnimatedVisibility(
                visible = currentRoute in topLevelRoutes,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(160, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { it / 2 },
            ) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        val route = dest.name.lowercase()
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigateTopLevel(route)
                            },
                            icon = {
                                Icon(
                                    when (dest) {
                                        TopDestination.Networks -> AppIcons.VpnKey
                                        TopDestination.Status -> AppIcons.Speed
                                        TopDestination.Settings -> Icons.Filled.Settings
                                    },
                                    // label 已提供文本，再写 contentDescription 会被 TalkBack 读两遍
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "networks",
            // consumeWindowInsets：消费掉已 pad 的 inset，编辑页内层 Scaffold 不会重复 pad navigationBars
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            // 顶层底栏切页保持轻量短淡入淡出；进出子页面（编辑页/DNS/Clash 帮助）采用轻微 Shared-Axis X 横向位移
            enterTransition = {
                val toSubPage = targetState.destination.route !in topLevelRoutes
                if (toSubPage) {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 8 }
                } else {
                    fadeIn(tween(160))
                }
            },
            exitTransition = {
                val toSubPage = targetState.destination.route !in topLevelRoutes
                if (toSubPage) {
                    fadeOut(tween(140)) +
                        slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 16 }
                } else {
                    fadeOut(tween(90))
                }
            },
            popEnterTransition = {
                val fromSubPage = initialState.destination.route !in topLevelRoutes
                if (fromSubPage) {
                    fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 16 }
                } else {
                    fadeIn(tween(160))
                }
            },
            popExitTransition = {
                val fromSubPage = initialState.destination.route !in topLevelRoutes
                if (fromSubPage) {
                    fadeOut(tween(150)) +
                        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 }
                } else {
                    fadeOut(tween(90))
                }
            },
        ) {
            composable("networks") {
                NetworksScreen(
                    container = container,
                    onCreateNetwork = { navController.navigate("editor/new") },
                    onEditNetwork = { id -> navController.navigate("editor/$id") },
                    onOpenStatus = { navController.navigateTopLevel("status") },
                )
            }
            composable("status") {
                StatusScreen(container = container)
            }
            composable("settings") {
                SettingsScreen(
                    onOpenDns = { navController.navigate("settings/dns") },
                    onOpenClashHelp = { navController.navigate("settings/clash") },
                )
            }
            composable("settings/dns") {
                DnsSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/clash") {
                ClashHelpScreen(onBack = { navController.popBackStack() })
            }
            composable("editor/{networkId}") { entry ->
                // "new" 是新建哨兵值，转成 null；否则 get("new") 查不到导致空渲染（黑屏）
                val id = entry.arguments?.getString("networkId")?.takeIf { it != "new" }
                NetworkEditorScreen(
                    container = container,
                    networkId = id,
                    onSaved = { _ -> navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * 底栏切页：每个顶层目的地独立返回栈。
 * 若用 navigate("status") 直接压在 networks 上面，再 popUpTo+restoreState
 * 会把状态页存进「网络」栈，表现为点「网络」还是状态页。
 */
private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
