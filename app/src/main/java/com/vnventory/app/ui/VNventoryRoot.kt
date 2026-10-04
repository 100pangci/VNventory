package com.vnventory.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.vnventory.app.ui.theme.ShelfMotion
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
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R

private data class TopLevelItem(
    val route: Any,
    @get:StringRes val label: Int,
    @get:DrawableRes val icon: Int,
    val isSelected: (NavDestination) -> Boolean,
)

private val topLevelItems = listOf(
    TopLevelItem(HomeRoute, R.string.nav_home, R.drawable.ic_ui_home) { it.hasRoute<HomeRoute>() },
    TopLevelItem(CollectionRoute, R.string.nav_collection, R.drawable.ic_ui_shelf) { it.hasRoute<CollectionRoute>() },
    TopLevelItem(OrdersRoute, R.string.nav_orders, R.drawable.ic_ui_batch) { it.hasRoute<OrdersRoute>() },
    TopLevelItem(SettingsRoute, R.string.nav_settings, R.drawable.ic_ui_preferences) { it.hasRoute<SettingsRoute>() },
)

@Composable
internal fun ShelfNavigationBar(selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier, tonalElevation = 0.dp, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        topLevelItems.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val scale = animateFloatAsState(if (selected) 1.06f else 1f, tween(ShelfMotion.Quick), label = "navIcon")
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(index) },
                icon = {
                    Icon(painterResource(item.icon), contentDescription = null,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value })
                },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** 底部栏切换（保留各自滚动位置/状态） */
@Composable
fun VNventoryRoot() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        topLevelItems.any { it.isSelected(destination) }
    } ?: true

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                showBottomBar,
                enter = expandVertically(tween(ShelfMotion.Chrome, easing = ShelfMotion.BackEasing)) + fadeIn(tween(ShelfMotion.Chrome)),
                exit = shrinkVertically(tween(ShelfMotion.Chrome, easing = ShelfMotion.BackEasing)) + fadeOut(tween(ShelfMotion.Chrome)),
            ) {
                ShelfNavigationBar(
                    selectedIndex = topLevelItems.indexOfFirst { item -> currentDestination?.hierarchy?.any { item.isSelected(it) } == true },
                    onSelect = { navController.navigateToTopLevel(topLevelItems[it].route) },
                )
            }
        },
    ) { innerPadding ->
        VNventoryNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            onNotice = { message -> scope.launch { snackbar.showSnackbar(message, withDismissAction = true) } },
        )
    }
}
