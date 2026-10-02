package com.vnventory.app.data.repository

import com.vnventory.app.core.AppResult
import com.vnventory.app.core.appResultOf
import com.vnventory.app.data.local.dao.VnCacheDao
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.mapper.toEntity
import com.vnventory.app.data.remote.vndb.VndbApi
import com.vnventory.app.data.remote.vndb.VndbApiException
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.domain.model.VnSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * VNDB 数据仓库：网络优先、写穿本地缓存、失败可回退缓存（标注 offline）。
 *
 * VNDB 数据只是「元数据缓存」；用户收藏永远以 owned_copy 为准。
 */
class VnRepository(
    private val api: VndbApi,
    private val vnCacheDao: VnCacheDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeCachedVn(vnId: String): Flow<VnInfo?> =
        vnCacheDao.observeVn(vnId).map { it?.toDomain() }.flowOn(io)

    fun observeCachedReleases(vnId: String): Flow<List<ReleaseInfo>> =
        vnCacheDao.observeReleases(vnId).map { list -> list.map { it.toDomain() } }.flowOn(io)

    suspend fun getCachedVn(vnId: String): VnInfo? =
        withContext(io) { vnCacheDao.getVn(vnId)?.toDomain() }

    suspend fun getCachedRelease(releaseId: String): ReleaseInfo? =
        withContext(io) { vnCacheDao.getRelease(releaseId)?.toDomain() }

    /**
     * 搜索 VN。网络失败且本地缓存有结果时，返回离线结果（[VnSearchResult.offline] = true）。
     */
    suspend fun searchVn(query: String, page: Int = 1): AppResult<VnSearchResult> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return AppResult.Success(VnSearchResult(emptyList()))

        val result = appResultOf {
            withContext(io) {
                val response = api.searchVn(keyword, page)
                val now = System.currentTimeMillis()
                response.results.forEach { dto ->
                    runCatching { vnCacheDao.upsertVn(dto.toDomain().toEntity(now)) }
                }
                VnSearchResult(
                    items = response.results.map { it.toDomain(fromCache = false) },
                    page = page,
                    hasMore = response.more,
                )
            }
        }

        if (result is AppResult.Success || page > 1) return result
        // 离线回退（仅第一页）
        val cached = withContext(io) { vnCacheDao.searchCached(keyword, limit = 30) }
        return if (cached.isNotEmpty()) {
            AppResult.Success(
                VnSearchResult(items = cached.map { it.toDomain() }, page = 1, hasMore = false, offline = true)
            )
        } else {
            result
        }
    }

    /** 拉取单个 VN 并写入缓存 */
    suspend fun fetchVn(vnId: String): AppResult<VnInfo> = appResultOf {
        withContext(io) {
            val dto = api.getVn(vnId) ?: throw VndbApiException("VNDB 未找到 VN：$vnId")
            val info = dto.toDomain()
            vnCacheDao.upsertVn(info.toEntity(System.currentTimeMillis()))
            info
        }
    }

    /** 拉取某 VN 的全部 Release 并写入缓存（新→旧） */
    suspend fun fetchReleases(vnId: String): AppResult<List<ReleaseInfo>> = appResultOf {
        withContext(io) {
            val coverFallback = vnCacheDao.getVn(vnId)?.imageUrl
            val response = api.getReleases(vnId)
            val now = System.currentTimeMillis()
            val infos = response.results.map { it.toDomain(vnId, coverFallback) }
            if (infos.isNotEmpty()) {
                vnCacheDao.upsertReleases(infos.map { it.toEntity(now) })
            }
            infos
        }
    }
}
