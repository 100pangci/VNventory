package com.vnventory.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vnventory.app.ui.navigation.CollectionRoute
import com.vnventory.app.ui.navigation.HomeRoute
import com.vnventory.app.ui.navigation.OrdersRoute
import com.vnventory.app.ui.navigation.SettingsRoute
import com.vnventory.app.ui.navigation.VNventoryNavHost
import com.vnventory.app.ui.navigation.navigateToTopLevel

private data class TopLevelItem(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val isSelected: (NavDestination) -> Boolean,
)

private val topLevelItems = listOf(
    TopLevelItem(HomeRoute, "首页", Icons.Filled.Home) { it.hasRoute<HomeRoute>() },
    TopLevelItem(CollectionRoute, "收藏", Icons.Filled.Favorite) { it.hasRoute<CollectionRoute>() },
    TopLevelItem(OrdersRoute, "订单", Icons.Filled.ShoppingCart) { it.hasRoute<OrdersRoute>() },
    TopLevelItem(SettingsRoute, "设置", Icons.Filled.Settings) { it.hasRoute<SettingsRoute>() },
)

/** 底部栏切换（保留各自滚动位置/状态） */
@Composable
fun VNventoryRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        topLevelItems.any { it.isSelected(destination) }
    } ?: true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { item.isSelected(it) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        VNventoryNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
