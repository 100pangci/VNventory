package com.vnventory.app.core

import com.vnventory.app.data.remote.vndb.VndbApiException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 统一的失败类型（UI 用来展示友好文案） */
enum class AppErrorKind { NETWORK, HTTP, PARSE, UNKNOWN }

data class AppError(
    val kind: AppErrorKind,
    val message: String,
    val cause: Throwable? = null,
)

/** 统一结果封装：可恢复错误不抛异常，交给 UI 展示与重试 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

fun <T> AppResult<T>.getOrDefault(default: T): T = (this as? AppResult.Success)?.data ?: default

/** 把可能抛异常的调用包装为 AppResult（取消异常必须原样抛出） */
suspend fun <T> appResultOf(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(e.toAppError())
}

fun Throwable.toAppError(): AppError = when (this) {
    is VndbApiException -> AppError(AppErrorKind.HTTP, message ?: "VNDB 请求失败", this)
    is SerializationException -> AppError(AppErrorKind.PARSE, "数据解析失败：${message ?: "格式不符"}", this)
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        AppError(AppErrorKind.NETWORK, "网络连接失败，请检查网络后重试", this)

    is IOException -> AppError(AppErrorKind.NETWORK, "网络请求失败：${message ?: "IO 错误"}", this)
    else -> AppError(AppErrorKind.UNKNOWN, message ?: "未知错误", this)
}
