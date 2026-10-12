package com.vnventory.app.data.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageResult
import com.vnventory.app.BuildConfig
import com.vnventory.app.data.remote.vndb.VndbClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

/**
 * 封面图片加载器（Coil 单例，由 [com.vnventory.app.VNventoryApp] 工厂创建）。
 *
 * - 磁盘缓存固定在 `filesDir/cover-cache`（而不是 `cacheDir`）：系统清理缓存、第三方清理工具
 *   或存储紧张时不会丢弃封面。封面属于收藏快照的展示数据；已加载过的封面在断网、
 *   国际链路拥塞或被阻断时仍能从本机显示。
 * - 网络请求带超时与两层有限重试（Ktor 传输层 + Coil 请求层）：中国大陆访问 VNDB CDN
 *   偶发连接重置、超时或读取中断；一次抖动不应直接变成“封面暂不可用”。
 */
object CoverImageLoader {

    internal const val DISK_CACHE_DIRECTORY = "cover-cache"

    /** 256MiB 上限、LRU 淘汰；约可容纳数百张完整封面与全部列表缩略图。 */
    internal const val DISK_CACHE_MAX_BYTES = 256L * 1024 * 1024

    fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(RetryCoverInterceptor())
            add(KtorNetworkFetcherFactory(httpClient = { coverHttpClient() }))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.filesDir, DISK_CACHE_DIRECTORY))
                .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()

    internal fun coverHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        // 注意顺序：要在 HttpTimeout 之前安装重试，超时异常才会进入重试判定（见 Ktor 文档）。
        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, response -> response.status.value >= 500 || response.status.value == 429 }
            retryOnExceptionIf { _, cause -> cause.isTransientNetworkFailure() }
            constantDelay(millis = 500, randomizationMs = 250)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
            // 完整大图在慢速链路上需要更长总时长；真正的卡死由 socket 超时兜底。
            requestTimeoutMillis = 60_000
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, VndbClientFactory.userAgent(BuildConfig.VERSION_NAME))
        }
    }
}

/**
 * Coil 请求级重试：覆盖发生在传输层重试范围之外的中断（例如下载到一半连接被重置）。
 * 解码等非网络失败不重试。
 */
private class RetryCoverInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        var attempt = 0
        while (true) {
            try {
                return chain.proceed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= MAX_RETRIES || !e.isTransientNetworkFailure()) throw e
                attempt++
                delay(RETRY_DELAY_MILLIS * attempt)
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MILLIS = 400L
    }
}

/** 连接重置、超时、DNS 失败、读取中断等可重试的网络异常；取消与解码错误不属于此列。 */
internal fun Throwable.isTransientNetworkFailure(): Boolean = this is IOException
