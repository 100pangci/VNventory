package com.vnventory.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * VNDB Release 元数据缓存。
 *
 * 一个 Release 可以对应多个 VN，关联由 ReleaseVnEntity 保存。
 */
@Entity(
    tableName = "release_cache",
)
data class ReleaseCacheEntity(
    /** VNDB 内部 ID，形如 `r12345` */
    @PrimaryKey val vndbId: String,
    val title: String,
    val released: String?,
    /** 逗号分隔：`win,psv,swi` */
    val platforms: String,
    /** 逗号分隔：`ja,en,zh-Hans` */
    val languages: String,
    /** 分号分隔的发行商名 */
    val publishers: String,
    val jan: String?,
    val minAge: Int?,
    val official: Boolean?,
    /** 实体包装图（VNDB 有则用，否则 UI 回落到 VN 封面） */
    val packagingImageUrl: String?,
    val fetchedAt: Long,
    val originalTitle: String? = null,
    val romanizedTitle: String? = null,
) {
    companion object {
        const val SEP_LIST = ","
        const val SEP_PUBLISHERS = ";"
    }
}

@Entity(
    tableName = "release_vn",
    primaryKeys = ["vnId", "releaseId"],
    foreignKeys = [
        ForeignKey(entity = VnCacheEntity::class, parentColumns = ["vndbId"], childColumns = ["vnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ReleaseCacheEntity::class, parentColumns = ["vndbId"], childColumns = ["releaseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("releaseId")],
)
data class ReleaseVnEntity(val vnId: String, val releaseId: String)
