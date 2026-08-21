package com.easytier.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EasyTierApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsState(
                initial = com.easytier.android.data.store.AppSettings(),
            )
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
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
            if (currentRoute in topLevelRoutes) {
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
                                    contentDescription = dest.label,
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
            modifier = Modifier.padding(padding),
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
                StatusScreen(
                    container = container,
                    initialNetworkName = null,
                )
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
