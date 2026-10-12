package com.vnventory.app.data.remote.fx

import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate

class FxRateSourceTest {

    private val today = LocalDate.of(2026, 10, 13)
    private var server: FakeHttpServer? = null

    @After fun tearDown() {
        server?.close()
    }

    private class FakeHttpServer(handler: (String) -> String?) {
        private val socket = ServerSocket(0)
        private val thread = Thread {
            try {
                while (!socket.isClosed) {
                    socket.accept().use { client -> respond(client, handler) }
                }
            } catch (_: Exception) {
                // 关闭时 accept 抛错，属于预期。
            }
        }.apply { isDaemon = true; start() }

        val port: Int get() = socket.localPort

        fun close() {
            socket.close()
            thread.join(5_000)
        }

        private fun respond(client: Socket, handler: (String) -> String?) {
            val reader = client.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = requestLine.split(' ').getOrNull(1) ?: "/"
            val body = handler(path)
            val bytes = (body ?: """{"message":"not found"}""").toByteArray(StandardCharsets.UTF_8)
            val status = if (body == null) "404 Not Found" else "200 OK"
            client.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                write(bytes)
                flush()
            }
        }
    }

    private fun client(): HttpClient = FxClientFactory.create(versionName = "test")

    private fun startServer(handler: (String) -> String?) = FakeHttpServer(handler).also { server = it }

    @Test fun `currency-api 解析汇率与实际日期`() = runBlocking {
        val http = startServer { path ->
            if (path == "/npm/@fawazahmed0/currency-api@2026-10-09/v1/currencies/jpy.min.json") {
                """{"date":"2026-10-09","jpy":{"cny":0.042299852,"twd":0.20172809}}"""
            } else null
        }
        val source = CurrencyApiSource(
            client = client(),
            jsDelivrBase = "http://127.0.0.1:${http.port}/npm/@fawazahmed0/currency-api",
            pagesDevTemplate = "http://127.0.0.1:${http.port}/pages/{date}",
        )
        val result = source.fetch("JPY", "CNY", LocalDate.of(2026, 10, 9))
        assertEquals(FxFetchResult.Success(42_299_852L, LocalDate.of(2026, 10, 9), "currency-api"), result)
    }

    @Test fun `currency-api 双镜像 404 视为尚未发布`() = runBlocking {
        val http = startServer { null }
        val source = CurrencyApiSource(
            client = client(),
            jsDelivrBase = "http://127.0.0.1:${http.port}/npm/@fawazahmed0/currency-api",
            pagesDevTemplate = "http://127.0.0.1:${http.port}/pages/{date}",
        )
        assertEquals(FxFetchResult.NotAvailable, source.fetch("JPY", "CNY", LocalDate.of(2026, 10, 13)))
    }

    @Test fun `currency-api 历史范围外直接不支持`() = runBlocking {
        val source = CurrencyApiSource(client = client())
        assertEquals(FxFetchResult.Unsupported, source.fetch("JPY", "CNY", LocalDate.of(2024, 3, 1)))
    }

    @Test fun `frankfurter 解析响应日期与汇率`() = runBlocking {
        val http = startServer { path ->
            if (path.startsWith("/frankfurter/2026-10-09")) {
                """{"amount":1.0,"base":"JPY","date":"2026-10-09","rates":{"CNY":0.04229}}"""
            } else null
        }
        val source = FrankfurterSource(client = client(), baseUrl = "http://127.0.0.1:${http.port}/frankfurter/")
        val result = source.fetch("JPY", "CNY", LocalDate.of(2026, 10, 9))
        assertEquals(FxFetchResult.Success(42_290_000L, LocalDate.of(2026, 10, 9), "frankfurter"), result)
    }

    @Test fun `frankfurter 近期 404 视为尚未发布`() = runBlocking {
        val http = startServer { null }
        val source = FrankfurterSource(client = client(), baseUrl = "http://127.0.0.1:${http.port}/frankfurter/", today = { today })
        assertEquals(FxFetchResult.NotAvailable, source.fetch("JPY", "TWD", LocalDate.of(2026, 10, 12)))
    }

    @Test fun `frankfurter 旧日期 404 视为不支持`() = runBlocking {
        val http = startServer { null }
        val source = FrankfurterSource(client = client(), baseUrl = "http://127.0.0.1:${http.port}/frankfurter/", today = { today })
        assertEquals(FxFetchResult.Unsupported, source.fetch("JPY", "TWD", LocalDate.of(2021, 6, 15)))
    }

    // ---- 双源链 ----

    private class FixedSource(override val name: String, private val result: (String, String, LocalDate) -> FxFetchResult) : FxRateSource {
        override suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult = result(from, to, date)
    }

    @Test fun `链上优先精确日期并接受周末回退`() = runBlocking {
        val saturday = LocalDate.of(2026, 10, 10)
        val client = FxRateClient(
            listOf(
                FixedSource("a") { _, _, _ -> FxFetchResult.NotAvailable },
                FixedSource("frankfurter") { _, _, _ -> FxFetchResult.Success(42_290_000L, LocalDate.of(2026, 10, 9), "frankfurter") },
            ),
            today = { today },
        )
        val result = client.fetch("JPY", "CNY", saturday)
        assertTrue(result is FxFetchResult.Success)
        assertEquals(LocalDate.of(2026, 10, 9), (result as FxFetchResult.Success).rateDate)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
    }

    @Test fun `链上近期工作日回退视为尚未发布`() = runBlocking {
        val client = FxRateClient(
            listOf(FixedSource("frankfurter") { _, _, _ -> FxFetchResult.Success(42_200_000L, LocalDate.of(2026, 10, 12), "frankfurter") }),
            today = { today },
        )
        // 请求 10-13（今天），只拿到 10-12：等待发布而不是替代。
        assertEquals(FxFetchResult.NotAvailable, client.fetch("JPY", "CNY", LocalDate.of(2026, 10, 13)))
    }

    @Test fun `链上较早日期接受交易日回退`() = runBlocking {
        val holiday = LocalDate.of(2026, 1, 1)
        val client = FxRateClient(
            listOf(FixedSource("frankfurter") { _, _, _ -> FxFetchResult.Success(44_690_000L, LocalDate.of(2025, 12, 31), "frankfurter") }),
            today = { today },
        )
        val result = client.fetch("JPY", "CNY", holiday)
        assertTrue(result is FxFetchResult.Success)
        assertEquals(LocalDate.of(2025, 12, 31), (result as FxFetchResult.Success).rateDate)
    }

    @Test fun `链上全部失败分类`() = runBlocking {
        val pending = FxRateClient(listOf(FixedSource("a") { _, _, _ -> FxFetchResult.NotAvailable }, FixedSource("b") { _, _, _ -> FxFetchResult.Failed }))
        assertEquals(FxFetchResult.NotAvailable, pending.fetch("JPY", "CNY", LocalDate.of(2026, 10, 13)))
        val unsupported = FxRateClient(listOf(FixedSource("a") { _, _, _ -> FxFetchResult.Unsupported }, FixedSource("b") { _, _, _ -> FxFetchResult.Failed }))
        assertEquals(FxFetchResult.Unsupported, unsupported.fetch("JPY", "CNY", LocalDate.of(2021, 1, 1)))
        val failed = FxRateClient(listOf(FixedSource("a") { _, _, _ -> FxFetchResult.Failed }))
        assertEquals(FxFetchResult.Failed, failed.fetch("JPY", "CNY", LocalDate.of(2026, 10, 13)))
    }
}
