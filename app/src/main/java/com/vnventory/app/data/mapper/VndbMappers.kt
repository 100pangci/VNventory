package com.vnventory.app.data.mapper

import com.vnventory.app.data.remote.vndb.VndbReleaseDto
import com.vnventory.app.data.remote.vndb.VndbVnDto
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo

// ---------------------------------------------------------------------------
// VNDB DTO -> Domain
// ---------------------------------------------------------------------------

fun VndbVnDto.toDomain(fromCache: Boolean = false): VnInfo = VnInfo(
    id = id,
    title = title,
    altTitle = originalTitle(),
    released = released,
    imageUrl = image?.url ?: image?.thumbnail,
    description = description,
    fromCache = fromCache,
)

/**
 * 原题（通常是日文标题）：
 * 优先取 languages 中标记 main 的日文标题；没有则取任一 main 标题；
 * 与展示标题相同则回退 alttitle；全都没有则为 null。
 */
internal fun VndbVnDto.originalTitle(): String? {
    val mainJa = titles.firstOrNull { it.main && it.lang == "ja" }?.title
    val mainAny = titles.firstOrNull { it.main }?.title
    return (mainJa ?: mainAny)?.takeIf { it.isNotBlank() && it != title } ?: alttitle
}

fun VndbReleaseDto.toDomain(vnId: String, fallbackCoverUrl: String? = null): ReleaseInfo = ReleaseInfo(
    id = id,
    vnId = vnId,
    title = title,
    released = released,
    platforms = platforms,
    languages = languages.mapNotNull { it.lang?.takeIf(String::isNotBlank) },
    publishers = publisherNames(),
    jan = gtin,
    minAge = minage,
    official = official,
    packagingImageUrl = packagingImageUrl(),
    coverImageUrl = fallbackCoverUrl,
)

/** 发行商：优先 publisher=true 的生产商；没有则退化为全部 */
internal fun VndbReleaseDto.publisherNames(): List<String> {
    val publishers = producers.filter { it.publisher == true }.map { it.name }.filter { it.isNotBlank() }
    return publishers.ifEmpty { producers.map { it.name }.filter { it.isNotBlank() } }
}

/** 实体包装图：优先正面包装（pkgfront），其次任意 pkg*；
 *  没有包装图时返回 null（UI 回退到 VN 封面，不拿宣传图/截图凑数） */
internal fun VndbReleaseDto.packagingImageUrl(): String? {
    val image = images.firstOrNull { it.type == "pkgfront" }
        ?: images.firstOrNull { it.type?.startsWith("pkg") == true }
    return image?.url ?: image?.thumbnail
}
