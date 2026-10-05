package com.vnventory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.vnventory.app.data.local.entity.ReleaseVnEntity
import com.vnventory.app.data.local.entity.ReleaseCacheEntity
import com.vnventory.app.data.local.entity.VnCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VnCacheDao {

    @Query("SELECT * FROM vn_cache WHERE vndbId = :vnId")
    fun observeVn(vnId: String): Flow<VnCacheEntity?>

    @Query("SELECT * FROM vn_cache WHERE vndbId = :vnId")
    suspend fun getVn(vnId: String): VnCacheEntity?

    @Upsert
    suspend fun upsertVn(vn: VnCacheEntity)

    @Query("SELECT r.* FROM release_cache r INNER JOIN release_vn v ON v.releaseId = r.vndbId WHERE v.vnId = :vnId ORDER BY r.released DESC, r.title ASC")
    fun observeReleases(vnId: String): Flow<List<ReleaseCacheEntity>>

    @Query("SELECT * FROM release_cache WHERE vndbId = :releaseId")
    suspend fun getRelease(releaseId: String): ReleaseCacheEntity?

    @Upsert
    suspend fun upsertReleases(releases: List<ReleaseCacheEntity>)

    @Upsert
    suspend fun upsertLinks(links: List<ReleaseVnEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM release_vn WHERE vnId = :vnId AND releaseId = :releaseId)")
    suspend fun isLinked(vnId: String, releaseId: String): Boolean

    @Query("DELETE FROM release_vn WHERE vnId = :vnId")
    suspend fun clearLinks(vnId: String)

    /** 仅当所有页成功后替换该 VN 的关联；保留其他 VN 的合辑关联及收藏快照。 */
    @Transaction
    suspend fun replaceReleases(vn: VnCacheEntity, releases: List<ReleaseCacheEntity>) {
        upsertVn(vn)
        upsertReleases(releases)
        clearLinks(vn.vndbId)
        upsertLinks(releases.map { ReleaseVnEntity(vn.vndbId, it.vndbId) })
    }

    /** 离线回退：只查本地缓存 */
    @Query(
        """
        SELECT * FROM vn_cache
        WHERE title LIKE '%' || :query || '%'
           OR IFNULL(altTitle, '') LIKE '%' || :query || '%'
           OR IFNULL(originalTitle, '') LIKE '%' || :query || '%'
           OR IFNULL(romanizedTitle, '') LIKE '%' || :query || '%'
           OR vndbId = :query
        ORDER BY title ASC
        LIMIT :limit
        """
    )
    suspend fun searchCached(query: String, limit: Int): List<VnCacheEntity>

    @Query("DELETE FROM vn_cache")
    suspend fun clearVnCache()

    @Query("DELETE FROM release_cache")
    suspend fun clearReleaseCache()
}
