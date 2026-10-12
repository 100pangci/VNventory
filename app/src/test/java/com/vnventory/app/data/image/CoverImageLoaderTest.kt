package com.vnventory.app.data.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverImageLoaderTest {

    /**
     * 封面磁盘缓存放进 filesDir：系统“清除缓存”、清理工具或存储紧张时不会丢封面，
     * 已加载过的封面在断网或受限网络下仍可显示。该测试防止误改回 cacheDir。
     */
    @Test fun `封面磁盘缓存位于应用数据目录而不是系统可清理缓存`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = CoverImageLoader.create(context)
        try {
            val directory = loader.diskCache?.directory?.toFile()
            assertNotNull(directory)
            assertEquals(
                File(context.filesDir, CoverImageLoader.DISK_CACHE_DIRECTORY).canonicalPath,
                directory!!.canonicalPath,
            )
            assertFalse(directory.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
        } finally {
            loader.shutdown()
        }
    }

    @Test fun `封面网络异常判定只接受可重试的 IO 故障`() {
        assertTrue(java.io.IOException("reset").isTransientNetworkFailure())
        assertTrue(java.net.SocketTimeoutException("timeout").isTransientNetworkFailure())
        assertFalse(IllegalStateException("decode").isTransientNetworkFailure())
    }
}
