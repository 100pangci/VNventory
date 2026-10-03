package com.vnventory.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.vnventory.app.data.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
            throw IllegalStateException("备份导出失败，请检查存储空间和文件权限", e)
        } catch (e: SecurityException) {
            throw IllegalStateException("无法写入此位置，请重新选择备份保存位置", e)
        }
    }

    suspend fun read(uri: Uri): BackupData = withContext(io) {
        try {
            (resolver.openInputStream(uri) ?: throw IOException()).use { BackupCodec.decode(it) }
        } catch (e: IOException) {
            throw IllegalStateException("备份文件读取失败，请检查文件是否可用", e)
        } catch (e: SecurityException) {
            throw IllegalStateException("无法读取此文件，请重新选择备份文件", e)
        }
    }
}
