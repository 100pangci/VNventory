package com.vnventory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedCopyDao {

    @Query("SELECT * FROM owned_copy WHERE id = :id")
    fun observeById(id: Long): Flow<OwnedCopyEntity?>

    @Query("SELECT * FROM owned_copy WHERE id = :id")
    suspend fun getById(id: Long): OwnedCopyEntity?

    /**
     * 收藏列表：排序与搜索在仓库层拼 SQL（参数全部绑定，无注入风险）。
     * 使用 RawQuery 是为了让「排序方式 + 搜索词」组合只维护一条查询逻辑。
     */
    @RawQuery(observedEntities = [OwnedCopyEntity::class])
    fun observeCollection(query: SupportSQLiteQuery): Flow<List<OwnedCopyEntity>>

    @Query("SELECT * FROM owned_copy ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OwnedCopyEntity>>

    @Query("SELECT * FROM owned_copy WHERE orderId = :orderId ORDER BY createdAt ASC, id ASC")
    fun observeByOrder(orderId: Long): Flow<List<OwnedCopyEntity>>

    @Query("SELECT * FROM owned_copy WHERE orderId = :orderId ORDER BY createdAt ASC, id ASC")
    suspend fun getByOrder(orderId: Long): List<OwnedCopyEntity>

    @Query("SELECT * FROM owned_copy")
    suspend fun getAll(): List<OwnedCopyEntity>

    @Query("SELECT * FROM owned_copy WHERE vnId = :vnId ORDER BY createdAt DESC")
    fun observeByVn(vnId: String): Flow<List<OwnedCopyEntity>>

    @Query("SELECT COUNT(*) FROM owned_copy")
    fun observeCopyCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT vnId) FROM owned_copy")
    fun observeDistinctVnCount(): Flow<Int>

    @Query("SELECT currency, SUM(priceMinor) AS total FROM owned_copy WHERE priceMinor IS NOT NULL GROUP BY currency")
    fun observePriceTotals(): Flow<List<CurrencyTotal>>

    @Query("SELECT COUNT(*) FROM owned_copy WHERE priceMinor IS NOT NULL")
    fun observePricedCopyCount(): Flow<Int>

    @Insert
    suspend fun insert(copy: OwnedCopyEntity): Long

    @Insert
    suspend fun insertAll(copies: List<OwnedCopyEntity>): List<Long>

    @Update
    suspend fun update(copy: OwnedCopyEntity)

    @Delete
    suspend fun delete(copy: OwnedCopyEntity)

    @Query("DELETE FROM owned_copy WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM owned_copy WHERE releaseId = :releaseId")
    fun observeCountForRelease(releaseId: String): Flow<Int>
}
