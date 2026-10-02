package com.vnventory.app.data.remote.vndb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** VNDB 返回非 2xx 时抛出，仓库层会转成友好错误 */
class VndbApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * VNDB Kana API 客户端。
 *
 * 只负责“发查询、拿 DTO”，不含缓存与业务逻辑（见 VnRepository）。
 * 全部使用 POST（VNDB 数据库查询 API 的约定）。
 */
class VndbApi(private val client: HttpClient) {

    /** VN 搜索（默认按搜索相关度排序） */
    suspend fun searchVn(query: String, page: Int = 1, results: Int = PAGE_SIZE): VndbVnResponse =
        post(
            path = "vn",
            body = VndbQueryBody(
                filters = simpleFilter("search", "=", query),
                fields = VN_FIELDS,
                sort = "searchrank",
                results = results,
                page = page,
            ),
        )

    /** 按 ID 取单个 VN；不存在返回 null */
    suspend fun getVn(vnId: String): VndbVnDto? =
        post<VndbVnResponse>(
            path = "vn",
            body = VndbQueryBody(
                filters = simpleFilter("id", "=", vnId),
                fields = VN_FIELDS,
                results = 1,
            ),
        ).results.firstOrNull()

    /**
     * 某 VN 的全部 Release（新→旧）。
     * 注意 VNDB 的 `vn` 是 match 型过滤器，值为嵌套的 VN 过滤器。
     */
    suspend fun getReleases(
        vnId: String,
        page: Int = 1,
        results: Int = RELEASE_PAGE_SIZE,
    ): VndbReleaseResponse =
        post(
            path = "release",
            body = VndbQueryBody(
                filters = buildJsonArray {
                    add(JsonPrimitive("vn"))
                    add(JsonPrimitive("="))
                    add(simpleFilter("id", "=", vnId))
                },
                fields = RELEASE_FIELDS,
                sort = "released",
                reverse = true,
                results = results,
                page = page,
            ),
        )

    private fun simpleFilter(field: String, operator: String, value: String): JsonArray =
        buildJsonArray {
            add(JsonPrimitive(field))
            add(JsonPrimitive(operator))
            add(JsonPrimitive(value))
        }

    private suspend inline fun <reified T> post(path: String, body: VndbQueryBody): T {
        val response = client.post(path) { setBody(body) }
        if (!response.status.isSuccess()) {
            val snippet = runCatching { response.bodyAsText().take(300) }.getOrDefault("")
            throw VndbApiException("VNDB 返回 HTTP ${response.status.value}：$snippet")
        }
        return response.body()
    }

    companion object {
        const val BASE_URL = "https://api.vndb.org/kana/"
        const val USER_AGENT = "VNventory/0.1.0 (Android; personal collection manager)"
        const val PAGE_SIZE = 20
        const val RELEASE_PAGE_SIZE = 100

        /** 实测于 2026-10（schema 见 https://api.vndb.org/kana/schema） */
        const val VN_FIELDS =
            "id,title,alttitle,released,description,image{url,thumbnail},titles{title,lang,main,official}"

        const val RELEASE_FIELDS =
            "id,title,alttitle,released,platforms,languages{lang,main}," +
                "producers{id,name,developer,publisher},gtin,minage,official,patch," +
                "images{id,type,url,thumbnail,dims,sexual,violence}"
    }
}
