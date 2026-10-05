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
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage
import com.vnventory.app.domain.text.requireNotNullMessage

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

    fun observePricedCopyCount(): Flow<Int> = ownedCopyDao.observePricedCopyCount().flowOn(io)

    fun observeDistinctVnCount(): Flow<Int> = ownedCopyDao.observeDistinctVnCount().flowOn(io)

    fun observePriceTotals(): Flow<List<CurrencyTotal>> = ownedCopyDao.observePriceTotals().flowOn(io)

    fun observeCopyCountForRelease(releaseId: String): Flow<Int> =
        ownedCopyDao.observeCountForRelease(releaseId).flowOn(io)

    suspend fun getById(id: Long): OwnedCopy? =
        withContext(io) { ownedCopyDao.getById(id)?.toDomain() }

    /** 新增（可一次多盒），返回新 ID 列表 */
    suspend fun addCopies(copies: List<OwnedCopy>): List<Long> = withContext(io) {
        database.withTransaction {
            copies.forEach { copy ->
                LocalRules.copy(copy)
                copy.releaseId?.let { requireMessage(vnCacheDao.isLinked(copy.vnId, it)) { message(MessageKey.RELEASE_VN_MISMATCH) } }
            }
            val ids = ownedCopyDao.insertAll(copies.map { it.copy(id = 0).toEntity() })
            copies.map { it.orderId }.distinct().forEach { LocalRules.order(database, it) }
            LocalRules.totals(database)
            ids
        }
    }

    suspend fun update(copy: OwnedCopy) = withContext(io) {
        database.withTransaction {
            LocalRules.copy(copy)
            val old = requireNotNullMessage(ownedCopyDao.getById(copy.id)) { message(MessageKey.COPY_MISSING) }
            requireMessage(copy.vnId == old.vnId && copy.releaseId == old.releaseId) { message(MessageKey.RELEASE_BIND_REQUIRED) }
            ownedCopyDao.update(copy.toEntity())
            LocalRules.order(database, copy.orderId)
            LocalRules.totals(database)
            // 订单归属可能变化：清理不再对应的手动分摊行
            expenseDao.pruneAllocationsForCopy(copy.id)
        }
    }

    suspend fun delete(id: Long) = withContext(io) {
        ownedCopyDao.deleteById(id) // 分摊行随 FK 级联删除
    }

    /** 将“手动版本”重新绑定到某个 VNDB Release（数据结构预留能力的落地） */
    suspend fun bindRelease(copyId: Long, release: ReleaseInfo, coverUrl: String?) = withContext(io) {
        database.withTransaction {
            val existing = requireNotNullMessage(ownedCopyDao.getById(copyId)) { message(MessageKey.COPY_MISSING) }
            requireMessage(release.vnId == existing.vnId && vnCacheDao.isLinked(existing.vnId, release.id)) {
                message(MessageKey.RELEASE_COPY_MISMATCH)
            }
            ownedCopyDao.update(
                existing.copy(
                    releaseId = release.id,
                    releaseTitle = release.displayTitle(com.vnventory.app.domain.model.TitleDisplayMode.ORIGINAL),
                    releaseOriginalTitle = release.originalTitle,
                    releaseRomanizedTitle = release.romanizedTitle,
                    coverUrl = coverUrl ?: existing.coverUrl,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun cachedVn(vnId: String): VnInfo? =
        withContext(io) { vnCacheDao.getVn(vnId)?.toDomain() }

    suspend fun cachedRelease(releaseId: String, vnId: String): ReleaseInfo? =
        withContext(io) { if (vnCacheDao.isLinked(vnId, releaseId)) vnCacheDao.getRelease(releaseId)?.toDomain(vnId) else null }

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
                OR IFNULL(vnOriginalTitle, '') LIKE '%' || ? || '%'
                OR IFNULL(vnRomanizedTitle, '') LIKE '%' || ? || '%'
                OR IFNULL(releaseOriginalTitle, '') LIKE '%' || ? || '%'
                OR IFNULL(releaseRomanizedTitle, '') LIKE '%' || ? || '%'
                OR IFNULL(shop, '') LIKE '%' || ? || '%'
                OR vnId = ?)
            """.trimIndent()
        }
        fun titleOrder(original: String, romanized: String, legacy: String, placeholder: String): String {
            val columns = if (query.titleDisplayMode == com.vnventory.app.domain.model.TitleDisplayMode.ORIGINAL) listOf(original, romanized) else listOf(romanized, original)
            return "COALESCE(" + (columns + legacy + placeholder).joinToString(",") { "NULLIF(TRIM($it), '')" } + ", '—') COLLATE NOCASE"
        }
        val vnOrder = titleOrder("vnOriginalTitle", "vnRomanizedTitle", "vnTitle", "vnId")
        val releaseOrder = "CASE WHEN releaseId IS NULL THEN TRIM(releaseTitle) ELSE " +
            titleOrder("releaseOriginalTitle", "releaseRomanizedTitle", "releaseTitle", "releaseId") + " END COLLATE NOCASE"
        val orderBy = when (query.sort) {
            CollectionSort.ADDED_DESC -> "createdAt DESC, id DESC"
            CollectionSort.ADDED_ASC -> "createdAt ASC, id ASC"
            CollectionSort.TITLE_ASC -> "$vnOrder ASC, $releaseOrder ASC, id ASC"
            CollectionSort.TITLE_DESC -> "$vnOrder DESC, $releaseOrder DESC, id DESC"
            CollectionSort.PURCHASE_DESC -> "(purchaseDate IS NULL) ASC, purchaseDate DESC, createdAt DESC"
            CollectionSort.PURCHASE_ASC -> "(purchaseDate IS NULL) ASC, purchaseDate ASC, createdAt ASC"
            CollectionSort.PRICE_DESC -> "(priceMinor IS NULL) ASC, priceMinor DESC, id DESC"
            CollectionSort.PRICE_ASC -> "(priceMinor IS NULL) ASC, priceMinor ASC, id ASC"
        }
        val sql = "SELECT * FROM owned_copy $where ORDER BY $orderBy"
        return if (keyword.isEmpty()) {
            SimpleSQLiteQuery(sql)
        } else {
            SimpleSQLiteQuery(sql, Array(8) { keyword })
        }
    }
}
