package com.vnventory.app.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.local.dao.CurrencyTotal
import com.vnventory.app.data.local.dao.ExpenseDao
import com.vnventory.app.data.local.dao.OwnedCopyDao
import com.vnventory.app.data.local.dao.VnCacheDao
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.mapper.toEntity
import com.vnventory.app.domain.model.CollectionQuery
import com.vnventory.app.domain.model.CollectionSort
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 收藏仓库：OwnedCopy 的增删改查。这是用户数据的事实来源。
 */
class CollectionRepository(
    private val database: VNventoryDatabase,
    private val ownedCopyDao: OwnedCopyDao,
    private val vnCacheDao: VnCacheDao,
    private val expenseDao: ExpenseDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeCollection(query: CollectionQuery): Flow<List<OwnedCopy>> =
        ownedCopyDao.observeCollection(buildCollectionQuery(query))
            .map { list -> list.map { it.toDomain() } }
            .flowOn(io)

    fun observeRecent(limit: Int = 8): Flow<List<OwnedCopy>> =
        ownedCopyDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }.flowOn(io)

    fun observeByVn(vnId: String): Flow<List<OwnedCopy>> =
        ownedCopyDao.observeByVn(vnId).map { list -> list.map { it.toDomain() } }.flowOn(io)

    fun observeById(id: Long): Flow<OwnedCopy?> =
        ownedCopyDao.observeById(id).map { it?.toDomain() }.flowOn(io)

    fun observeCopyCount(): Flow<Int> = ownedCopyDao.observeCopyCount().flowOn(io)

    fun observeDistinctVnCount(): Flow<Int> = ownedCopyDao.observeDistinctVnCount().flowOn(io)

    fun observePriceTotals(): Flow<List<CurrencyTotal>> = ownedCopyDao.observePriceTotals().flowOn(io)

    fun observeCopyCountForRelease(releaseId: String): Flow<Int> =
        ownedCopyDao.observeCountForRelease(releaseId).flowOn(io)

    suspend fun getById(id: Long): OwnedCopy? =
        withContext(io) { ownedCopyDao.getById(id)?.toDomain() }

    /** 新增（可一次多盒），返回新 ID 列表 */
    suspend fun addCopies(copies: List<OwnedCopy>): List<Long> = withContext(io) {
        ownedCopyDao.insertAll(copies.map { it.copy(id = 0).toEntity() })
    }

    suspend fun update(copy: OwnedCopy) = withContext(io) {
        database.withTransaction {
            ownedCopyDao.update(copy.toEntity())
            // 订单归属可能变化：清理不再对应的手动分摊行
            expenseDao.pruneAllocationsForCopy(copy.id)
        }
    }

    suspend fun delete(id: Long) = withContext(io) {
        ownedCopyDao.deleteById(id) // 分摊行随 FK 级联删除
    }

    /** 将“手动版本”重新绑定到某个 VNDB Release（数据结构预留能力的落地） */
    suspend fun bindRelease(copyId: Long, release: ReleaseInfo, coverUrl: String?) = withContext(io) {
        val existing = ownedCopyDao.getById(copyId) ?: return@withContext
        ownedCopyDao.update(
            existing.copy(
                releaseId = release.id,
                releaseTitle = release.title,
                coverUrl = coverUrl ?: existing.coverUrl,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun cachedVn(vnId: String): VnInfo? =
        withContext(io) { vnCacheDao.getVn(vnId)?.toDomain() }

    suspend fun cachedRelease(releaseId: String): ReleaseInfo? =
        withContext(io) { vnCacheDao.getRelease(releaseId)?.toDomain() }

    // ------------------------------------------------------------------
    // 收藏列表查询：排序 + 搜索（参数绑定，SQL 白名单拼接）
    // ------------------------------------------------------------------

    private fun buildCollectionQuery(query: CollectionQuery): SupportSQLiteQuery {
        val keyword = query.search.trim()
        val where = if (keyword.isEmpty()) {
            ""
        } else {
            """
            WHERE (vnTitle LIKE '%' || ? || '%'
                OR IFNULL(releaseTitle, '') LIKE '%' || ? || '%'
                OR IFNULL(shop, '') LIKE '%' || ? || '%'
                OR vnId = ?)
            """.trimIndent()
        }
        val orderBy = when (query.sort) {
            CollectionSort.ADDED_DESC -> "createdAt DESC, id DESC"
            CollectionSort.ADDED_ASC -> "createdAt ASC, id ASC"
            CollectionSort.TITLE_ASC -> "vnTitle COLLATE NOCASE ASC, releaseTitle COLLATE NOCASE ASC, id ASC"
            CollectionSort.TITLE_DESC -> "vnTitle COLLATE NOCASE DESC, releaseTitle COLLATE NOCASE DESC, id DESC"
            CollectionSort.PURCHASE_DESC -> "(purchaseDate IS NULL) ASC, purchaseDate DESC, createdAt DESC"
            CollectionSort.PURCHASE_ASC -> "(purchaseDate IS NULL) ASC, purchaseDate ASC, createdAt ASC"
            CollectionSort.PRICE_DESC -> "priceMinor DESC, id DESC"
            CollectionSort.PRICE_ASC -> "priceMinor ASC, id ASC"
        }
        val sql = "SELECT * FROM owned_copy $where ORDER BY $orderBy"
        return if (keyword.isEmpty()) {
            SimpleSQLiteQuery(sql)
        } else {
            SimpleSQLiteQuery(sql, arrayOf(keyword, keyword, keyword, keyword))
        }
    }
}
