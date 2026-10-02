package com.vnventory.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * VNDB Release 元数据缓存。
 *
 * 注意：VNDB 中一个 Release 可能属于多个 VN；本表按「用户在哪个 VN 下看到的」
 * 记录所属 VN（缓存用途，非严格关系建模）。
 */
@Entity(
    tableName = "release_cache",
    foreignKeys = [
        ForeignKey(
            entity = VnCacheEntity::class,
            parentColumns = ["vndbId"],
            childColumns = ["vnId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("vnId")],
)
data class ReleaseCacheEntity(
    /** VNDB 内部 ID，形如 `r12345` */
    @PrimaryKey val vndbId: String,
    val vnId: String?,
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
) {
    companion object {
        const val SEP_LIST = ","
        const val SEP_PUBLISHERS = ";"
    }
}
