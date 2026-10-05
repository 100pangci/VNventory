package com.vnventory.app.data.mapper

import com.vnventory.app.data.remote.vndb.VndbReleaseDto
import com.vnventory.app.data.remote.vndb.VndbVnDto
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.domain.model.normalizedTitle

// ---------------------------------------------------------------------------
// VNDB DTO -> Domain
// ---------------------------------------------------------------------------

fun VndbVnDto.toDomain(fromCache: Boolean = false): VnInfo = VnInfo(
    id = id,
    legacyTitle = title,
    legacyAltTitle = alttitle,
    originalTitle = originalTitle(),
    romanizedTitle = titles.firstOrNull { it.main }?.latin.normalizedTitle() ?: title.normalizedTitle(),
    released = released,
    imageUrl = image?.url ?: image?.thumbnail,
    description = description,
    fromCache = fromCache,
)

/**
 * 原题：优先 main.title，空白或缺失时依次回退 alttitle / 顶层 title。
 * 不按语言或字符集猜测原题，也不使用其它语言的官方译名覆盖 main。
 * 同名 ASCII 原题仍保留。旧缓存标题不推断类型，仅作为 legacy fallback。
 */
internal fun VndbVnDto.originalTitle(): String? {
    val main = titles.firstOrNull { it.main }
    return main?.title.normalizedTitle()
        ?: alttitle.normalizedTitle()
        ?: title.normalizedTitle()
}

fun VndbReleaseDto.toDomain(vnId: String, fallbackCoverUrl: String? = null): ReleaseInfo = ReleaseInfo(
    id = id,
    vnId = vnId,
    legacyTitle = title,
    originalTitle = alttitle.normalizedTitle(),
    romanizedTitle = title.normalizedTitle(),
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
