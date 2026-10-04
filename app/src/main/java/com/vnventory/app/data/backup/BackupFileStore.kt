package com.vnventory.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.vnventory.app.data.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.MessageStateException
import com.vnventory.app.domain.text.message

/** 只通过系统文档选择器授予的 URI 读写，无需存储权限或持久 URI 授权。 */
class BackupFileStore(
    private val resolver: ContentResolver,
    private val repository: BackupRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun export(uri: Uri) = withContext(io) {
        // 先生成完整文件再打开输出流；编码失败不会截断目标文档。
        try {
            val bytes = BackupCodec.encode(repository.snapshot())
            (resolver.openOutputStream(uri, "wt") ?: throw IOException()).use { it.write(bytes) }
        } catch (e: IOException) {
            throw MessageStateException(message(MessageKey.BACKUP_EXPORT_FAILED), e)
        } catch (e: SecurityException) {
            throw MessageStateException(message(MessageKey.BACKUP_EXPORT_DENIED), e)
        }
    }

    suspend fun read(uri: Uri): BackupData = withContext(io) {
        try {
            (resolver.openInputStream(uri) ?: throw IOException()).use { BackupCodec.decode(it) }
        } catch (e: IOException) {
            throw MessageStateException(message(MessageKey.BACKUP_READ_FAILED), e)
        } catch (e: SecurityException) {
            throw MessageStateException(message(MessageKey.BACKUP_READ_DENIED), e)
        }
    }
}
