package com.vnventory.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.vnventory.app.ui.navigation.navigateToTopLevel

@Composable
fun VNventoryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
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
                onOrderCreated = { navController.navigate(OrderDetailRoute(it)) },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen()
        }

        composable<AddRoute> { entry ->
            val route: AddRoute = entry.toRoute()
            AddFlowScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                orderContextId = route.orderId,
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
                onSaved = { navController.popBackStack() },
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
