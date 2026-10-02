package com.vnventory.app.domain.model

/** 品相：全新 / 中古 / 未拆 / 缺件 / 自定义 */
enum class CopyCondition(val label: String) {
    NEW("全新"),
    USED("中古"),
    UNOPENED("未拆"),
    INCOMPLETE("缺件"),
    CUSTOM("自定义"),
}

/** 费用分摊方式 */
enum class AllocationMode(val label: String) {
    EQUAL("平均分摊"),
    BY_PRICE("按价格比例"),
    MANUAL("手动指定"),
}

/** 费用分类（名称仍自由填写，分类仅用于汇总展示） */
enum class ExpenseCategory(val label: String) {
    SHIPPING("运费"),
    FEE("手续费"),
    TAX("税费"),
    OTHER("其他"),
}
