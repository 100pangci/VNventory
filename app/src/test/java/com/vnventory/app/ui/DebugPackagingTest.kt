package com.vnventory.app.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.BuildConfig
import com.vnventory.app.MainActivity
import com.vnventory.app.VNventoryApp
import com.vnventory.app.ui.preview.ShelfPreviewData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebugPackagingTest {
    @Test fun `Debug安装包独立包名但Application和Activity类仍使用源码namespace`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(BuildConfig.DEBUG)
        assertEquals("com.vnventory.app.debug", BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
        assertEquals(VNventoryApp::class.java.name, context.applicationInfo.className)
        val activity = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals("com.vnventory.app.MainActivity", activity.name)
    }

    @Test fun `预览封面使用当前Debug包名而不是Release包名`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShelfPreviewData.copies.forEach {
            val uri = Uri.parse(it.coverUrl)
            assertEquals("android.resource", uri.scheme)
            assertEquals(context.packageName, uri.authority)
            assertNotNull(context.getDrawable(uri.lastPathSegment!!.toInt()))
        }
    }
}
