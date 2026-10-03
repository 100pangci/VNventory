package com.vnventory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity

/** 调用方必须用 Room 事务取得完整快照或替换购买事实；元数据缓存不动。 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM purchase_order ORDER BY id")
    suspend fun getOrders(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM expense_allocation ORDER BY expenseId, ownedCopyId")
    suspend fun getAllocations(): List<ExpenseAllocationEntity>

    @Query("DELETE FROM owned_copy")
    suspend fun deleteCopies()

    @Query("DELETE FROM purchase_order")
    suspend fun deleteOrders()
}
