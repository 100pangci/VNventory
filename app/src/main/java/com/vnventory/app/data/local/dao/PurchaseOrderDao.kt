package com.vnventory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseOrderDao {

    @Transaction
    @Query("SELECT * FROM purchase_order WHERE id = :id")
    fun observeGraph(id: Long): Flow<OrderGraph?>

    @Query(
        """
        SELECT * FROM purchase_order
        ORDER BY CASE WHEN orderDate IS NULL THEN 1 ELSE 0 END, orderDate DESC, createdAt DESC
        """
    )
    fun observeAll(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_order WHERE id = :id")
    fun observeById(id: Long): Flow<PurchaseOrderEntity?>

    @Query("SELECT * FROM purchase_order WHERE id = :id")
    suspend fun getById(id: Long): PurchaseOrderEntity?

    /** 每个订单的盒子数与本体价合计（按币种），用于订单列表/详情统计 */
    @Query(
        """
        SELECT orderId AS orderId, currency AS currency,
               SUM(priceMinor) AS total, COUNT(*) AS copyCount
        FROM owned_copy
        WHERE orderId IS NOT NULL
        GROUP BY orderId, currency
        """
    )
    fun observeGoodsTotals(): Flow<List<OrderGoodsTotal>>

    @Insert
    suspend fun insert(order: PurchaseOrderEntity): Long

    @Update
    suspend fun update(order: PurchaseOrderEntity)

    @Delete
    suspend fun delete(order: PurchaseOrderEntity)

    @Query("DELETE FROM purchase_order WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 删除订单后，仍然挂在它名下的盒子数量（删除前提示用） */
    @Query("SELECT COUNT(*) FROM owned_copy WHERE orderId = :orderId")
    suspend fun countCopies(orderId: Long): Int
}
