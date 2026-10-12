package com.vnventory.app.data.remote.fx

import com.vnventory.app.domain.model.Fx
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.Locale

/**
 * frankfurter.dev：ECB 官方参考汇率，历史到 1999；不支持 TWD 等 ECB 未发布币种。
 *
 * 周末 / 节假日会返回最近交易日的汇率，响应 `date` 字段给出实际日期。
 */
class FrankfurterSource(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val today: () -> LocalDate = LocalDate::now,
) : FxRateSource {

    override val name = SOURCE_NAME

    override suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult {
        if (date < HISTORY_START) return FxFetchResult.Unsupported
        val url = "$baseUrl$date?base=${from.uppercase(Locale.ROOT)}&symbols=${to.uppercase(Locale.ROOT)}"
        val response = try {
            client.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return FxFetchResult.Failed
        }
        if (!response.status.isSuccess()) {
            return if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest) {
                // 近期日期多为“尚未发布”；旧日期多为币种 / 范围不支持。
                if (date >= today().minusDays(RECENT_DAYS)) FxFetchResult.NotAvailable else FxFetchResult.Unsupported
            } else {
                FxFetchResult.Failed
            }
        }
        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return FxFetchResult.Failed
        val parsed = parse(body, to) ?: return FxFetchResult.Failed
        return FxFetchResult.Success(parsed.first, parsed.second, name)
    }

    private fun parse(body: String, to: String): Pair<Long, LocalDate>? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val rateDate = root["date"]?.jsonPrimitive?.content
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val rateText = root["rates"]?.jsonObject?.get(to.uppercase(Locale.ROOT))?.jsonPrimitive?.content ?: return null
        val rate = Fx.fromDecimal(rateText) ?: return null
        return rate to rateDate
    }

    companion object {
        const val SOURCE_NAME = "frankfurter"
        const val DEFAULT_BASE_URL = "https://api.frankfurter.dev/v1/"
        /** ECB 欧元参考汇率从 1999-01-04 开始。 */
        val HISTORY_START: LocalDate = LocalDate.of(1999, 1, 4)
        const val RECENT_DAYS = 7L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
