package com.vnventory.app.core

import com.vnventory.app.data.remote.vndb.VndbApiException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageFailure
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

/** 统一的失败类型（UI 用来展示友好文案） */
enum class AppErrorKind { NETWORK, HTTP, PARSE, UNKNOWN }

data class AppError(
    val kind: AppErrorKind,
    val message: Message,
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
    is VndbApiException -> AppError(AppErrorKind.HTTP, userMessage, this)
    is MessageFailure -> AppError(AppErrorKind.UNKNOWN, userMessage, this)
    is SerializationException -> AppError(AppErrorKind.PARSE, message(MessageKey.ERROR_PARSE), this)
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        AppError(AppErrorKind.NETWORK, message(MessageKey.ERROR_NETWORK), this)

    is IOException -> AppError(AppErrorKind.NETWORK, message(MessageKey.ERROR_IO), this)
    else -> AppError(AppErrorKind.UNKNOWN, this.message?.let(Message::Literal) ?: message(MessageKey.ERROR_UNKNOWN), this)
}
