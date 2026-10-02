package com.vnventory.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VNDB VN 元数据缓存。
 *
 * 纯缓存：可以被清空/重建，不影响用户收藏（owned_copy 里保存了快照）。
 */
@Entity(tableName = "vn_cache")
data class VnCacheEntity(
    /** VNDB 内部 ID，形如 `v17` */
    @PrimaryKey val vndbId: String,
    val title: String,
    val altTitle: String?,
    /** 原始发售日期文本，VNDB 可能是 `2026-09-25` 或 `2026` 等不完整格式，保持字符串 */
    val released: String?,
    val imageUrl: String?,
    val description: String?,
    /** 缓存写入时间（epoch millis） */
    val fetchedAt: Long,
)
