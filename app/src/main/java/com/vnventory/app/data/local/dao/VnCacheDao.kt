package com.vnventory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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

    @Query("SELECT * FROM release_cache WHERE vnId = :vnId ORDER BY released DESC, title ASC")
    fun observeReleases(vnId: String): Flow<List<ReleaseCacheEntity>>

    @Query("SELECT * FROM release_cache WHERE vndbId = :releaseId")
    suspend fun getRelease(releaseId: String): ReleaseCacheEntity?

    @Upsert
    suspend fun upsertReleases(releases: List<ReleaseCacheEntity>)

    /** 离线回退：只查本地缓存 */
    @Query(
        """
        SELECT * FROM vn_cache
        WHERE title LIKE '%' || :query || '%'
           OR IFNULL(altTitle, '') LIKE '%' || :query || '%'
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
