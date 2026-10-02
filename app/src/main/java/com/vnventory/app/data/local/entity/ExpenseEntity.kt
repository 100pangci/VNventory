package com.vnventory.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.ExpenseCategory

/**
 * 通用费用条目（名称自由填写）。
 *
 * 分摊结果不落库（EQUAL / BY_PRICE 实时计算），
 * 只有 MANUAL 模式在 [ExpenseAllocationEntity] 保存手动指定的金额。
 */
@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    /** 例：日本国内运费、国际运费、支付手续费、税费 */
    val name: String,
    /** 费用分类（展示名见 [ExpenseCategory.label]） */
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    /** 分摊方式（展示名见 [AllocationMode.label]） */
    val mode: AllocationMode,
    val notes: String?,
    val createdAt: Long,
)

/**
 * 手动分摊明细（仅 MANUAL 模式使用）。
 */
@Entity(
    tableName = "expense_allocation",
    primaryKeys = ["expenseId", "ownedCopyId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OwnedCopyEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownedCopyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ownedCopyId")],
)
data class ExpenseAllocationEntity(
    val expenseId: Long,
    val ownedCopyId: Long,
    val amountMinor: Long,
)
