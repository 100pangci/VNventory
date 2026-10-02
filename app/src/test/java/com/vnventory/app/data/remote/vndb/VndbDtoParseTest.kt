package com.vnventory.app.data.remote.vndb

import com.vnventory.app.data.mapper.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VNDB DTO 解析测试。
 * JSON 样本取自 2026-10 对 api.vndb.org/kana 的真实响应（已截取字段）。
 */
class VndbDtoParseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // ---- VN ----

    private val vnJson = """
    {
      "more": false,
      "results": [
        {
          "alttitle": "うたわれるもの",
          "description": "Hakuoro wakes up ...",
          "id": "v3",
          "image": {
            "thumbnail": "https://t.vndb.org/cv.t/27/89127.jpg",
            "url": "https://t.vndb.org/cv/27/89127.jpg"
          },
          "released": "2002-04-26",
          "title": "Utawarerumono",
          "titles": [
            {"lang": "en", "main": false, "official": true, "title": "Utawarerumono"},
            {"lang": "ja", "main": true, "official": true, "title": "うたわれるもの"},
            {"lang": "zh-Hans", "main": false, "official": true, "title": "传颂之物"}
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `解析 VN 响应`() {
        val response = json.decodeFromString<VndbVnResponse>(vnJson)
        assertEquals(1, response.results.size)
        val vn = response.results[0]
        assertEquals("v3", vn.id)
        assertEquals("Utawarerumono", vn.title)
        assertEquals("2002-04-26", vn.released)
        assertEquals("https://t.vndb.org/cv/27/89127.jpg", vn.image?.url)
        assertEquals(3, vn.titles.size)
    }

    @Test
    fun `原题取日文 main 标题`() {
        val vn = json.decodeFromString<VndbVnResponse>(vnJson).results[0].toDomain()
        assertEquals("うたわれるもの", vn.altTitle)
        assertEquals("Utawarerumono", vn.title)
    }

    @Test
    fun `原题与标题相同则不重复展示`() {
        val dto = VndbVnDto(
            id = "v17",
            title = "Ever17 -the out of infinity-",
            alttitle = null,
            titles = listOf(
                VndbTitleDto(title = "Ever17 -the out of infinity-", lang = "ja", main = true),
            ),
        )
        assertNull(dto.toDomain().altTitle)
    }

    // ---- Release ----

    private val releaseJson = """
    {
      "results": [
        {
          "id": "r280",
          "title": "Utawarerumono - Limited Edition",
          "released": "2002-04-26",
          "platforms": ["win"],
          "languages": [{"lang": "ja", "main": true}],
          "producers": [
            {"id": "p8", "name": "KID", "developer": true, "publisher": true}
          ],
          "gtin": "4946569500413",
          "minage": 15,
          "official": true,
          "images": [
            {"id": "cv78613", "type": "pkgfront",
             "url": "https://t.vndb.org/cv/13/78613.jpg",
             "thumbnail": "https://t.vndb.org/cv.t/13/78613.jpg",
             "dims": [1024, 1022], "sexual": 0, "violence": 0}
          ]
        },
        {
          "id": "r999",
          "title": "Download edition",
          "released": "TBA",
          "platforms": ["web"],
          "languages": [],
          "producers": [],
          "gtin": null,
          "minage": null,
          "official": false,
          "images": []
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `解析 Release 响应`() {
        val response = json.decodeFromString<VndbReleaseResponse>(releaseJson)
        assertEquals(2, response.results.size)
        val release = response.results[0]
        assertEquals("r280", release.id)
        assertEquals(listOf("win"), release.platforms)
        assertEquals("ja", release.languages[0].lang)
        assertEquals("4946569500413", release.gtin)
        assertEquals("pkgfront", release.images[0].type)
    }

    @Test
    fun `DTO 映射到领域模型`() {
        val release = json.decodeFromString<VndbReleaseResponse>(releaseJson).results[0]
            .toDomain(vnId = "v3", fallbackCoverUrl = "https://t.vndb.org/cv/27/89127.jpg")

        assertEquals("v3", release.vnId)
        assertEquals(listOf("KID"), release.publishers)
        assertEquals(listOf("ja"), release.languages)
        assertEquals("4946569500413", release.jan)
        // 包装图优先
        assertEquals("https://t.vndb.org/cv/13/78613.jpg", release.displayImage())
    }

    @Test
    fun `无包装图时退回 VN 封面`() {
        val release = json.decodeFromString<VndbReleaseResponse>(releaseJson).results[1]
            .toDomain(vnId = "v3", fallbackCoverUrl = "https://t.vndb.org/cv/27/89127.jpg")

        assertNull(release.packagingImageUrl)
        assertEquals("https://t.vndb.org/cv/27/89127.jpg", release.displayImage())
        assertEquals(emptyList<String>(), release.publishers)
        assertTrue(release.official == false)
    }

    @Test
    fun `非 pkg 图不作为包装图`() {
        val dto = VndbReleaseDto(
            id = "r1",
            images = listOf(
                VndbImageDto(id = "x", type = "dig", url = "https://t.vndb.org/dig.jpg"),
            ),
        )
        assertNull(dto.toDomain("v1").packagingImageUrl)
    }

    @Test
    fun `发行商取 publisher 标记 无标记则退化全部`() {
        val onlyDeveloper = VndbReleaseDto(
            id = "r1",
            producers = listOf(
                VndbProducerDto(id = "p1", name = "DevOnly", developer = true, publisher = false),
            ),
        )
        assertEquals(listOf("DevOnly"), onlyDeveloper.toDomain("v1").publishers)
    }

    // ---- 查询请求体序列化 ----

    @Test
    fun `查询请求体序列化包含 filters 与 fields`() {
        val body = VndbQueryBody(
            filters = kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("vn"))
                add(kotlinx.serialization.json.JsonPrimitive("="))
                add(
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("id"))
                        add(kotlinx.serialization.json.JsonPrimitive("="))
                        add(kotlinx.serialization.json.JsonPrimitive("v17"))
                    }
                )
            },
            fields = "id,title",
            sort = "released",
            reverse = true,
            results = 10,
        )
        val encoded = Json.encodeToString(VndbQueryBody.serializer(), body)
        assertTrue(encoded.contains(""""filters":["vn","=",["id","=","v17"]]"""))
        assertTrue(encoded.contains(""""reverse":true"""))
    }
}
