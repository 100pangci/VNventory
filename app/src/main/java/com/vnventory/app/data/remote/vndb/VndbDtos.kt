package com.vnventory.app.data.remote.vndb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------------------
// VNDB Kana API v2 数据模型（字段由 2026-10 实测 schema 确定）
// https://api.vndb.org/kana/schema
// ---------------------------------------------------------------------------

@Serializable
data class VndbImageDto(
    val id: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    val dims: List<Int>? = null,
    val sexual: Double? = null,
    val violence: Double? = null,
    /** 包装图类型：pkgfront / pkgback / pkgcontent / pkgside / dig / promo … */
    val type: String? = null,
)

@Serializable
data class VndbTitleDto(
    val title: String = "",
    val lang: String? = null,
    val main: Boolean = false,
    val official: Boolean = false,
)

@Serializable
data class VndbVnDto(
    val id: String,
    val title: String = "",
    val alttitle: String? = null,
    val released: String? = null,
    val description: String? = null,
    val image: VndbImageDto? = null,
    val titles: List<VndbTitleDto> = emptyList(),
)

@Serializable
data class VndbLanguageDto(
    val lang: String? = null,
    val main: Boolean = false,
    val mtl: Boolean = false,
)

@Serializable
data class VndbProducerDto(
    val id: String? = null,
    val name: String = "",
    val developer: Boolean? = null,
    val publisher: Boolean? = null,
)

@Serializable
data class VndbReleaseDto(
    val id: String,
    val title: String = "",
    val alttitle: String? = null,
    val released: String? = null,
    val platforms: List<String> = emptyList(),
    val languages: List<VndbLanguageDto> = emptyList(),
    val producers: List<VndbProducerDto> = emptyList(),
    /** JAN / EAN / UPC 等条码（VNDB 统一命名为 gtin） */
    val gtin: String? = null,
    val minage: Int? = null,
    val official: Boolean? = null,
    val patch: Boolean? = null,
    val images: List<VndbImageDto> = emptyList(),
)

@Serializable
data class VndbVnResponse(
    val results: List<VndbVnDto> = emptyList(),
    val more: Boolean = false,
    val count: Int? = null,
)

@Serializable
data class VndbReleaseResponse(
    val results: List<VndbReleaseDto> = emptyList(),
    val more: Boolean = false,
    val count: Int? = null,
)

/** 查询请求体（filters 用 JsonElement 直接构造，保持与 VNDB 文档一致的灵活语法） */
@Serializable
data class VndbQueryBody(
    val filters: JsonElement,
    val fields: String,
    val sort: String? = null,
    val reverse: Boolean? = null,
    val results: Int? = null,
    val page: Int? = null,
)
