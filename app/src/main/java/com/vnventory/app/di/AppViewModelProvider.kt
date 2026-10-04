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
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireNotNullMessage

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
                copyId = requireNotNullMessage(createSavedStateHandle().get<Long>("copyId")) {
                    message(MessageKey.NAV_ARG_MISSING, "copyId")
                },
            )
        }

        initializer {
            CopyEditViewModel(
                container = appContainer(),
                copyId = requireNotNullMessage(createSavedStateHandle().get<Long>("copyId")) {
                    message(MessageKey.NAV_ARG_MISSING, "copyId")
                },
            )
        }

        initializer {
            OrderDetailViewModel(
                container = appContainer(),
                orderId = requireNotNullMessage(createSavedStateHandle().get<Long>("orderId")) {
                    message(MessageKey.NAV_ARG_MISSING, "orderId")
                },
            )
        }
    }
}

private fun CreationExtras.appContainer(): AppContainer {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VNventoryApp
    return app.container
}
