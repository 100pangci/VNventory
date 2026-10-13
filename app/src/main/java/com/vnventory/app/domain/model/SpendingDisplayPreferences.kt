package com.vnventory.app.domain.model

/** 首页消费统计的币种展示偏好；currency 为空时跟随应用默认货币。 */
data class SpendingDisplayPreferences(
    val unifiedCurrency: Boolean = false,
    val currency: String? = null,
)
