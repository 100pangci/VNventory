package com.vnventory.app.domain.model

import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

/** 收藏列表排序方式 */
enum class CollectionSort(private val labelKey: MessageKey) {
    ADDED_DESC(MessageKey.SORT_ADDED_DESC),
    ADDED_ASC(MessageKey.SORT_ADDED_ASC),
    TITLE_ASC(MessageKey.SORT_TITLE_ASC),
    TITLE_DESC(MessageKey.SORT_TITLE_DESC),
    PURCHASE_DESC(MessageKey.SORT_PURCHASE_DESC),
    PURCHASE_ASC(MessageKey.SORT_PURCHASE_ASC),
    PRICE_DESC(MessageKey.SORT_PRICE_DESC),
    PRICE_ASC(MessageKey.SORT_PRICE_ASC);

    val label get() = message(labelKey)
}

/** 收藏列表的查询参数 */
data class CollectionQuery(
    val search: String = "",
    val sort: CollectionSort = CollectionSort.ADDED_DESC,
)
