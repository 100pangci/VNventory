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
 * fawazahmed0/currency-api：覆盖广（含 TWD），按日期出包，jsDelivr 与 pages.dev 双镜像。
 *
 * 该数据源的历史约从 2024-03-02 开始；更早的日期交给 frankfurter 或手动填写。
 * 当天的包通常在稍后发布：缺失时返回 [FxFetchResult.NotAvailable]，由上层自动重试。
 */
class CurrencyApiSource(
    private val client: HttpClient,
    private val jsDelivrBase: String = DEFAULT_JS_DELIVR_BASE,
    private val pagesDevTemplate: String = DEFAULT_PAGES_DEV_TEMPLATE,
) : FxRateSource {

    override val name = SOURCE_NAME

    override suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult {
        if (date < HISTORY_START) return FxFetchResult.Unsupported
        val base = from.lowercase(Locale.ROOT)
        val target = to.lowercase(Locale.ROOT)
        val urls = listOf(
            "$jsDelivrBase@$date/v1/currencies/$base.min.json",
            pagesDevTemplate.replace("{date}", date.toString()) + "/v1/currencies/$base.min.json",
        )
        var sawNotFound = false
        for (url in urls) {
            val response = try {
                client.get(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            if (!response.status.isSuccess()) {
                if (response.status == HttpStatusCode.NotFound) sawNotFound = true
                continue
            }
            val body = runCatching { response.bodyAsText() }.getOrNull() ?: continue
            val parsed = parse(body, base, target) ?: continue
            return FxFetchResult.Success(parsed.first, parsed.second, name)
        }
        return if (sawNotFound) FxFetchResult.NotAvailable else FxFetchResult.Failed
    }

    private fun parse(body: String, base: String, target: String): Pair<Long, LocalDate>? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val rateDate = root["date"]?.jsonPrimitive?.content
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val rateText = root[base]?.jsonObject?.get(target)?.jsonPrimitive?.content ?: return null
        val rate = Fx.fromDecimal(rateText) ?: return null
        return rate to rateDate
    }

    companion object {
        const val SOURCE_NAME = "currency-api"
        const val DEFAULT_JS_DELIVR_BASE = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api"
        const val DEFAULT_PAGES_DEV_TEMPLATE = "https://{date}.currency-api.pages.dev"
        /** 实测该数据源的发行包从 2024-03-02 起；更早日期不再尝试。 */
        val HISTORY_START: LocalDate = LocalDate.of(2024, 3, 2)
        private val json = Json { ignoreUnknownKeys = true }
    }
}
