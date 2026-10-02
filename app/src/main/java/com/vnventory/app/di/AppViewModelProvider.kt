package com.vnventory.app.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vnventory.app.VNventoryApp
import com.vnventory.app.ui.add.AddFlowViewModel
import com.vnventory.app.ui.collection.CollectionViewModel
import com.vnventory.app.ui.detail.CopyDetailViewModel
import com.vnventory.app.ui.edit.CopyEditViewModel
import com.vnventory.app.ui.home.HomeViewModel
import com.vnventory.app.ui.orders.OrderDetailViewModel
import com.vnventory.app.ui.orders.OrdersViewModel
import com.vnventory.app.ui.settings.SettingsViewModel

/**
 * ViewModel 工厂：手动注入 AppContainer。
 *
 * 带导航参数的 VM 从 SavedStateHandle 取参数（与 typed routes 的属性名一致）。
 */
object AppViewModelProvider {

    val Factory = viewModelFactory {

        initializer { HomeViewModel(appContainer()) }

        initializer { CollectionViewModel(appContainer()) }

        initializer { OrdersViewModel(appContainer()) }

        initializer { SettingsViewModel(appContainer()) }

        initializer {
            AddFlowViewModel(
                container = appContainer(),
                orderIdArg = createSavedStateHandle().get<Long>("orderId"),
            )
        }

        initializer {
            CopyDetailViewModel(
                container = appContainer(),
                copyId = checkNotNull(createSavedStateHandle().get<Long>("copyId")) {
                    "缺少导航参数 copyId"
                },
            )
        }

        initializer {
            CopyEditViewModel(
                container = appContainer(),
                copyId = checkNotNull(createSavedStateHandle().get<Long>("copyId")) {
                    "缺少导航参数 copyId"
                },
            )
        }

        initializer {
            OrderDetailViewModel(
                container = appContainer(),
                orderId = checkNotNull(createSavedStateHandle().get<Long>("orderId")) {
                    "缺少导航参数 orderId"
                },
            )
        }
    }
}

private fun CreationExtras.appContainer(): AppContainer {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VNventoryApp
    return app.container
}
