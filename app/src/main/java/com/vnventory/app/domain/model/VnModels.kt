package com.vnventory.app.domain.model

/**
 * VNDB VN 信息（领域模型；来自网络或本地缓存）。
 *
 * @param fromCache 是否为离线缓存数据（UI 会标注）
 */
data class VnInfo(
    val id: String,
    val title: String,
    val altTitle: String?,
    val released: String?,
    val imageUrl: String?,
    val description: String?,
    val fromCache: Boolean = false,
) {
    /** 原文（优先日语）作为主标题；保留 VNDB 拉丁字标题供搜索和辅助显示。 */
    val displayTitle: String get() = altTitle?.takeIf { it.isNotBlank() } ?: title
    val secondaryTitle: String? get() = title.takeIf { it.isNotBlank() && it != displayTitle }
}

/**
 * VNDB Release 信息（领域模型）。
 *
 * @param packagingImageUrl 实体包装图（VNDB 有则用）
 * @param coverImageUrl 兜底封面（通常取所属 VN 的封面）
 */
data class ReleaseInfo(
    val id: String,
    val vnId: String,
    val title: String,
    val released: String?,
    val platforms: List<String>,
    val languages: List<String>,
    val publishers: List<String>,
    val jan: String?,
    val minAge: Int?,
    val official: Boolean?,
    val packagingImageUrl: String?,
    val coverImageUrl: String?,
) {
    fun displayImage(): String? = packagingImageUrl ?: coverImageUrl
}

/** VN 搜索结果（offline=true 表示来自本地缓存） */
data class VnSearchResult(
    val items: List<VnInfo>,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val offline: Boolean = false,
)
