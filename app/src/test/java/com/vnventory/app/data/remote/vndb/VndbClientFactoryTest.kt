package com.vnventory.app.data.remote.vndb

import io.ktor.http.HttpHeaders
import com.vnventory.app.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class VndbClientFactoryTest {

    @Test
    fun `all VNDB endpoints inherit the versioned User-Agent`() = verifyUserAgent("1.2.3")

    @Test fun `actual BuildConfig version is used on every VNDB request`() = verifyUserAgent(BuildConfig.VERSION_NAME)

    private fun verifyUserAgent(version: String) = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val requests = mutableListOf<Pair<String, String?>>()
            val serverFailure = AtomicReference<Throwable?>()
            val serverThread = Thread {
                try {
                    repeat(3) {
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            val request = readRequest(socket)
                            requests += request
                            writeJsonResponse(socket)
                        }
                    }
                } catch (failure: Throwable) {
                    serverFailure.set(failure)
                }
            }.apply {
                isDaemon = true
                start()
            }

            val client = VndbClientFactory.create(
                versionName = version,
                baseUrl = "http://127.0.0.1:${server.localPort}/kana/",
            )
            try {
                val api = VndbApi(client)
                api.searchVn("example")
                api.getVn("v1")
                api.getReleases("v1")
            } finally {
                client.close()
            }

            serverThread.join(10_000)
            assertFalse("local VNDB test server did not finish", serverThread.isAlive)
            serverFailure.get()?.let { throw AssertionError("local VNDB test server failed", it) }
            assertEquals(listOf("/kana/vn", "/kana/vn", "/kana/release"), requests.map { it.first })
            assertEquals(
                listOf("VNventory/$version (Android; personal collection manager)"),
                requests.map { it.second }.distinct(),
            )
            assertEquals(3, requests.size)
        }
    }

    private fun readRequest(socket: Socket): Pair<String, String?> {
        val reader = socket.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
        val requestLine = checkNotNull(reader.readLine())
        var userAgent: String? = null
        while (true) {
            val line = checkNotNull(reader.readLine())
            if (line.isEmpty()) break
            if (line.substringBefore(':').equals(HttpHeaders.UserAgent, ignoreCase = true)) {
                userAgent = line.substringAfter(':').trim()
            }
        }
        return requestLine.split(' ')[1] to userAgent
    }

    private fun writeJsonResponse(socket: Socket) {
        val body = """{"more":false,"results":[]}"""
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(StandardCharsets.ISO_8859_1))
            write(bytes)
            flush()
        }
    }
}
