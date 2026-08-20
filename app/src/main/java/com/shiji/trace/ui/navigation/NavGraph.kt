// 时迹 —— 导航图
// 底部 4 个主标签页 + 详情页栈（M3 起接入真实页面）

package com.shiji.trace.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shiji.trace.R
import com.shiji.trace.TimelineApp
import com.shiji.trace.ui.screens.appdetail.AppDetailScreen
import com.shiji.trace.ui.screens.privacy.PrivacyPolicyScreen
import com.shiji.trace.ui.screens.settings.SettingsScreen
import com.shiji.trace.ui.screens.stats.StatsScreen
import com.shiji.trace.ui.screens.timeline.TimelineScreen
import com.shiji.trace.ui.screens.today.TodayScreen

// —— 底部导航标签定义 ——

/** 导航目的地 */
private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination("today", R.string.nav_today, Icons.Filled.Home),
    TopLevelDestination("timeline", R.string.nav_timeline, Icons.Filled.Schedule),
    TopLevelDestination("stats", R.string.nav_stats, Icons.Filled.BarChart),
    TopLevelDestination("settings", R.string.nav_settings, Icons.Filled.Settings),
)

/**
 * 应用导航图
 * 底部导航栏 + 4 个主页面（当前骨架为占位，M3 起接真实数据）
 */
@Composable
fun ShiJiNavGraph() {
    val navController = rememberNavController()
    // 依赖容器（从应用上下文获取）
    val appContext = LocalContext.current.applicationContext as TimelineApp
    val container = appContext.container
    // 当前所在页面（用于底部导航高亮）
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            // 点击底部导航：跳转到目标页，避免重复入栈
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("today") { TodayScreen(container) }
            composable("timeline") { TimelineScreen(container) }
            composable("stats") {
                // 排行点击 → 应用详情页
                StatsScreen(container, onAppClick = { pkg ->
                    navController.navigate("appdetail?package=$pkg")
                })
            }
            composable("settings") {
                // 设置页 → 隐私政策页
                SettingsScreen(container, onPrivacyClick = {
                    navController.navigate("privacy")
                })
            }
            // 隐私政策页（M6 起）：内置静态文本，不联网
            composable("privacy") {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            // 应用详情页（M5 起）：单应用曲线 + 时段分布 + 会话列表
            composable(
                route = "appdetail?package={packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { entry ->
                AppDetailScreen(
                    container = container,
                    packageName = entry.arguments?.getString("packageName") ?: "",
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
