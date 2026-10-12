package com.vnventory.app.data.remote.fx

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 汇率接口客户端工厂；与 VNDB 客户端相同的时间参数与 User-Agent 约定。 */
object FxClientFactory {

    fun create(
        versionName: String,
        debugLogging: Boolean = false,
    ): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        if (debugLogging) {
            install(Logging) {
                // 只记 HEADERS，不输出响应正文。
                level = LogLevel.HEADERS
            }
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
            headers.append(HttpHeaders.UserAgent, userAgent(versionName))
        }
    }

    internal fun userAgent(versionName: String): String =
        "VNventory/$versionName (Android; personal collection manager)"
}
