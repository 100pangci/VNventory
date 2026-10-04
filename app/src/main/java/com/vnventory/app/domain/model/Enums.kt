package com.vnventory.app.domain.model

import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

/** 品相：全新 / 中古 / 未拆 / 缺件 / 自定义 */
enum class CopyCondition(private val labelKey: MessageKey) {
    NEW(MessageKey.CONDITION_NEW),
    USED(MessageKey.CONDITION_USED),
    UNOPENED(MessageKey.CONDITION_UNOPENED),
    INCOMPLETE(MessageKey.CONDITION_INCOMPLETE),
    CUSTOM(MessageKey.CONDITION_CUSTOM);

    val label get() = message(labelKey)
}

/** 费用分摊方式 */
enum class AllocationMode(private val labelKey: MessageKey) {
    EQUAL(MessageKey.ALLOCATION_EQUAL),
    BY_PRICE(MessageKey.ALLOCATION_BY_PRICE),
    MANUAL(MessageKey.ALLOCATION_MANUAL);

    val label get() = message(labelKey)
}

/** 费用分类（名称仍自由填写，分类仅用于汇总展示） */
enum class ExpenseCategory(private val labelKey: MessageKey) {
    SHIPPING(MessageKey.CATEGORY_SHIPPING),
    FEE(MessageKey.CATEGORY_FEE),
    TAX(MessageKey.CATEGORY_TAX),
    OTHER(MessageKey.CATEGORY_OTHER);

    val label get() = message(labelKey)
}
