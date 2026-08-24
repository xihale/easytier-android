package com.easytier.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.easytier.android.ui.screens.settings.SettingsScreen
import com.easytier.android.ui.screens.status.StatusScreen
import com.easytier.android.ui.theme.EasyTierTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EasyTierApp).container
        // 同步读一次初始设置（DataStore 文件极小）：collectAsState 用真实初值，避免首帧闪默认主题
        val initialSettings = runBlocking { container.settingsRepository.settings.first() }
        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = initialSettings)
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
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 2 },
            ) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        val route = dest.name.lowercase()
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { navController.navigateTopLevel(route) },
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
                            label = { Text(dest.label) },
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
            // 纯短淡入淡出：slide 会带着重组中的重页面一起动画，是切页卡顿的主因
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(90)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(90)) },
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
                SettingsScreen()
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
