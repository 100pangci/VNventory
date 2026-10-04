package com.vnventory.app.ui.navigation

import kotlinx.serialization.Serializable

// 类型安全的导航路由（Navigation Compose typed routes）

@Serializable
object HomeRoute

@Serializable
object CollectionRoute

@Serializable
object OrdersRoute

@Serializable
object SettingsRoute

@Serializable
object SettingsPreferencesRoute

@Serializable
object SettingsShopsRoute

@Serializable
object SettingsDataRoute

@Serializable
object SettingsAboutRoute

/** 添加收藏流程；orderId 非空表示“加入该订单”上下文 */
@Serializable
data class AddRoute(val orderId: Long? = null)

@Serializable
data class CopyDetailRoute(val copyId: Long)

@Serializable
data class CopyEditRoute(val copyId: Long)

@Serializable
data class OrderDetailRoute(val orderId: Long)
