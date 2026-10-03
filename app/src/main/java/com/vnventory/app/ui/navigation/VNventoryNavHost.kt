package com.vnventory.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.vnventory.app.ui.add.AddFlowScreen
import com.vnventory.app.ui.collection.CollectionScreen
import com.vnventory.app.ui.detail.CopyDetailScreen
import com.vnventory.app.ui.edit.CopyEditScreen
import com.vnventory.app.ui.home.HomeScreen
import com.vnventory.app.ui.orders.OrderDetailScreen
import com.vnventory.app.ui.orders.OrdersScreen
import com.vnventory.app.ui.settings.SettingsScreen
import com.vnventory.app.ui.settings.SettingsPreferencesScreen
import com.vnventory.app.ui.settings.SettingsDataScreen
import com.vnventory.app.ui.settings.SettingsAboutScreen
import com.vnventory.app.ui.navigation.navigateToTopLevel
import com.vnventory.app.ui.theme.ShelfMotion

@Composable
fun VNventoryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onNotice: (String) -> Unit = {},
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = {
            if (targetState.destination.isShelfRoot()) fadeIn(tween(ShelfMotion.Standard, delayMillis = 70))
            else fadeIn(tween(ShelfMotion.Standard)) + slideInHorizontally(tween(ShelfMotion.Navigation, easing = ShelfMotion.Easing)) { it / 12 * direction }
        },
        exitTransition = { fadeOut(tween(ShelfMotion.Quick)) },
        popEnterTransition = { ShelfMotion.backEnter(direction) },
        popExitTransition = { ShelfMotion.backExit(direction) },
        predictivePopEnterTransition = { edge -> ShelfMotion.predictiveBackEnter(edge) },
        predictivePopExitTransition = { edge -> ShelfMotion.predictiveBackExit(edge) },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onAddClick = { navController.navigate(AddRoute()) },
                onCopyClick = { navController.navigate(CopyDetailRoute(it)) },
                onSeeAllClick = { navController.navigateToTopLevel(CollectionRoute) },
            )
        }

        composable<CollectionRoute> {
            CollectionScreen(
                onAddClick = { navController.navigate(AddRoute()) },
                onCopyClick = { navController.navigate(CopyDetailRoute(it)) },
            )
        }

        composable<OrdersRoute> {
            OrdersScreen(
                onOrderClick = { navController.navigate(OrderDetailRoute(it)) },
                onOrderCreated = { onNotice("购买批次已创建"); navController.navigate(OrderDetailRoute(it)) },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onPreferences = { navController.navigate(SettingsPreferencesRoute) },
                onData = { navController.navigate(SettingsDataRoute) },
                onAbout = { navController.navigate(SettingsAboutRoute) },
            )
        }

        composable<SettingsPreferencesRoute> {
            SettingsPreferencesScreen(onBack = { navController.popBackStack() })
        }

        composable<SettingsDataRoute> {
            SettingsDataScreen(onBack = { navController.popBackStack() })
        }

        composable<SettingsAboutRoute> {
            SettingsAboutScreen(onBack = { navController.popBackStack() })
        }

        composable<AddRoute> {
            AddFlowScreen(
                onBack = { navController.popBackStack() },
                onSaved = { count -> navController.popBackStack(); onNotice("已将 $count 盒收藏放入书架") },
            )
        }

        composable<CopyDetailRoute> { entry ->
            val route: CopyDetailRoute = entry.toRoute()
            CopyDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(CopyEditRoute(it)) },
                onOrderClick = { navController.navigate(OrderDetailRoute(it)) },
            )
        }

        composable<CopyEditRoute> {
            CopyEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack(); onNotice("收藏信息已更新") },
            )
        }

        composable<OrderDetailRoute> { entry ->
            val route: OrderDetailRoute = entry.toRoute()
            OrderDetailScreen(
                onBack = { navController.popBackStack() },
                onAddCopies = { navController.navigate(AddRoute(orderId = it)) },
                onCopyClick = { navController.navigate(CopyDetailRoute(it)) },
            )
        }
    }
}

private fun NavDestination.isShelfRoot(): Boolean =
    hasRoute<HomeRoute>() || hasRoute<CollectionRoute>() || hasRoute<OrdersRoute>() || hasRoute<SettingsRoute>()
