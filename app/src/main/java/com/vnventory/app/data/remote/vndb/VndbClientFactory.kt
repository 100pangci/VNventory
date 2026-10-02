package com.vnventory.app.data.remote.vndb

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

object VndbClientFactory {

    fun create(debugLogging: Boolean = false): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true      // VNDB 增加字段时保持兼容
                    explicitNulls = false
                    isLenient = false
                }
            )
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        if (debugLogging) {
            install(Logging) {
                level = LogLevel.HEADERS
            }
        }

        defaultRequest {
            url(VndbApi.BASE_URL)
            contentType(ContentType.Application.Json)
            headers.append(HttpHeaders.UserAgent, VndbApi.USER_AGENT)
        }
    }
}
