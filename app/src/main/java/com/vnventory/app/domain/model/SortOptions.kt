package com.vnventory.app.domain.model

/** 收藏列表排序方式 */
enum class CollectionSort(val label: String) {
    ADDED_DESC("最近添加"),
    ADDED_ASC("最早添加"),
    TITLE_ASC("标题 A→Z"),
    TITLE_DESC("标题 Z→A"),
    PURCHASE_DESC("购买时间（新→旧）"),
    PURCHASE_ASC("购买时间（旧→新）"),
    PRICE_DESC("价格（高→低）"),
    PRICE_ASC("价格（低→高）"),
}

/** 收藏列表的查询参数 */
data class CollectionQuery(
    val search: String = "",
    val sort: CollectionSort = CollectionSort.ADDED_DESC,
)
