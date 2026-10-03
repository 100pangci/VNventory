package com.vnventory.app.data.repository

import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy

/** 必须在写事务内调用，所有写入入口共享这些约束。 */
internal object LocalRules {
    fun currency(value: String) {
        require(value == Money.normalize(value) && value.matches(Regex("[A-Z]{3}"))) { "币种须使用三位大写代码" }
    }

    fun copy(copy: OwnedCopy) {
        require(copy.priceMinor >= 0) { "购入价格不能为负数" }
        require(copy.vnId.matches(Regex("v[1-9][0-9]*"))) { "VN ID 不正确" }
        require(copy.vnTitle.isNotBlank() && !copy.releaseTitle.isNullOrBlank()) { "作品名和版本名不能为空" }
        require(copy.condition != CopyCondition.CUSTOM || !copy.conditionNote.isNullOrBlank()) { "请填写自定义品相说明" }
        currency(copy.currency)
    }

    suspend fun order(db: VNventoryDatabase, id: Long?) {
        if (id == null) return
        require(db.purchaseOrderDao().getById(id) != null) { "所选订单已不存在，请重新选择" }
        val mixed = db.ownedCopyDao().getByOrder(id).map { it.currency }.distinct().size > 1
        require(!mixed || db.expenseDao().getByOrder(id).none { it.mode == AllocationMode.BY_PRICE }) {
            "本订单有按价格比例分摊的费用，请先改为平均分摊或手动指定，再加入不同币种的商品"
        }
    }

    suspend fun totals(db: VNventoryDatabase) {
        // Checked arithmetic before commit also protects SQL SUM projections from overflow.
        Money.totals(db.ownedCopyDao().getAll().map { it.currency to it.priceMinor } +
            db.expenseDao().getAll().map { it.currency to it.amountMinor })
    }
}
