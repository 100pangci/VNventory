package com.vnventory.app.data.repository

import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage

/** 必须在写事务内调用，所有写入入口共享这些约束。 */
internal object LocalRules {
    fun currency(value: String) {
        requireMessage(value == Money.normalize(value) && value.matches(Regex("[A-Z]{3}"))) { message(MessageKey.CURRENCY_INVALID) }
    }

    fun copy(copy: OwnedCopy) {
        requireMessage(copy.priceMinor >= 0) { message(MessageKey.COPY_PRICE_NEGATIVE) }
        requireMessage(copy.vnId.matches(Regex("v[1-9][0-9]*"))) { message(MessageKey.VN_ID_INVALID) }
        requireMessage(copy.vnTitle.isNotBlank() && !copy.releaseTitle.isNullOrBlank()) { message(MessageKey.TITLES_REQUIRED) }
        requireMessage(copy.condition != CopyCondition.CUSTOM || !copy.conditionNote.isNullOrBlank()) { message(MessageKey.CONDITION_NOTE_REQUIRED) }
        currency(copy.currency)
    }

    suspend fun order(db: VNventoryDatabase, id: Long?) {
        if (id == null) return
        requireMessage(db.purchaseOrderDao().getById(id) != null) { message(MessageKey.ORDER_SELECTED_MISSING) }
        val mixed = db.ownedCopyDao().getByOrder(id).map { it.currency }.distinct().size > 1
        requireMessage(!mixed || db.expenseDao().getByOrder(id).none { it.mode == AllocationMode.BY_PRICE }) {
            message(MessageKey.ORDER_MIXED_PRICE_ALLOCATION)
        }
    }

    suspend fun totals(db: VNventoryDatabase) {
        // Checked arithmetic before commit also protects SQL SUM projections from overflow.
        Money.totals(db.ownedCopyDao().getAll().map { it.currency to it.priceMinor } +
            db.expenseDao().getAll().map { it.currency to it.amountMinor })
    }
}
