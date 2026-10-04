package com.vnventory.app.data.local.dao

/** 收藏总价：按币种分组 */
data class CurrencyTotal(
    val currency: String,
    val total: Long,
)

/** 费用合计：按分类 + 币种分组（首页统计） */
data class CategoryTotal(
    val category: String,
    val currency: String,
    val total: Long,
)

/** 订单商品合计：某订单下所有盒子的本体价，按币种分组 */
data class OrderGoodsTotal(
    val orderId: Long,
    val currency: String,
    val total: Long?,
    val copyCount: Int,
    val pricedCopyCount: Int,
)

/** 订单费用合计：某订单的全部费用，按币种分组 */
data class OrderFeeTotal(
    val orderId: Long,
    val currency: String,
    val total: Long,
)
