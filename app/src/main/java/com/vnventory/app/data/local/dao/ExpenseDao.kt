package com.vnventory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expense WHERE orderId = :orderId ORDER BY createdAt ASC, id ASC")
    fun observeByOrder(orderId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE orderId = :orderId ORDER BY createdAt ASC, id ASC")
    suspend fun getByOrder(orderId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expense")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expense WHERE id = :id")
    fun observeById(id: Long): Flow<ExpenseEntity?>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- 手动分摊明细 ----

    @Query("SELECT * FROM expense_allocation WHERE expenseId = :expenseId")
    fun observeAllocations(expenseId: Long): Flow<List<ExpenseAllocationEntity>>

    @Query("SELECT * FROM expense_allocation WHERE expenseId = :expenseId")
    suspend fun getAllocations(expenseId: Long): List<ExpenseAllocationEntity>

    /** 整个订单的手动分摊（前端一次取回后在内存中按 expenseId 分组） */
    @Query(
        """
        SELECT a.* FROM expense_allocation a
        INNER JOIN expense e ON e.id = a.expenseId
        WHERE e.orderId = :orderId
        """
    )
    fun observeAllocationsForOrder(orderId: Long): Flow<List<ExpenseAllocationEntity>>

    @Upsert
    suspend fun upsertAllocations(allocations: List<ExpenseAllocationEntity>)

    @Query("DELETE FROM expense_allocation WHERE expenseId = :expenseId")
    suspend fun clearAllocations(expenseId: Long)

    /**
     * 清理“失效”的手动分摊：某盒被移出了某笔费用所属的订单时，
     * 删除对应的分摊行（不删除费用本身）。
     */
    @Query(
        """
        DELETE FROM expense_allocation
        WHERE ownedCopyId = :copyId
          AND expenseId IN (
              SELECT e.id FROM expense e
              WHERE e.orderId IS NOT (SELECT c.orderId FROM owned_copy c WHERE c.id = :copyId)
          )
        """
    )
    suspend fun pruneAllocationsForCopy(copyId: Long)

    /** 首页费用统计：按分类 + 币种汇总 */
    @Query("SELECT category, currency, SUM(amountMinor) AS total FROM expense GROUP BY category, currency")
    fun observeCategoryTotals(): Flow<List<CategoryTotal>>

    /** 订单列表用：每个订单的全部费用，按币种分组 */
    @Query("SELECT orderId, currency, SUM(amountMinor) AS total FROM expense GROUP BY orderId, currency")
    fun observeOrderFeeTotals(): Flow<List<OrderFeeTotal>>
}
