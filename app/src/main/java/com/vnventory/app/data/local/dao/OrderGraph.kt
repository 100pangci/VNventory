package com.vnventory.app.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity

data class ExpenseGraph(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "id", entityColumn = "expenseId")
    val allocations: List<ExpenseAllocationEntity>,
)

/** 单次 Room 事务快照，避免 combine 多条查询时短暂混用新旧商品/费用。 */
data class OrderGraph(
    @Embedded val order: PurchaseOrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val copies: List<OwnedCopyEntity>,
    @Relation(parentColumn = "id", entityColumn = "orderId", entity = ExpenseEntity::class)
    val expenses: List<ExpenseGraph>,
)
