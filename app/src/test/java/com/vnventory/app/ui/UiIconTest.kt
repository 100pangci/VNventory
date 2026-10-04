package com.vnventory.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiIconTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val icons = listOf(R.drawable.ic_ui_home, R.drawable.ic_ui_shelf, R.drawable.ic_ui_batch,
        R.drawable.ic_ui_preferences, R.drawable.ic_ui_shop, R.drawable.ic_ui_backup, R.drawable.ic_ui_info,
        R.drawable.ic_ui_grid, R.drawable.ic_ui_list, R.drawable.ic_ui_sort)

    @Test fun `功能图标统一24坐标和圆角线条而非位图或同一占位图`() {
        val namespace = "http://schemas.android.com/apk/res/android"
        val contours = mutableSetOf<String>()
        icons.forEach { id ->
            context.resources.getXml(id).use { xml ->
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        assertTrue(xml.name in listOf("vector", "path"))
                        if (xml.name == "vector") {
                            assertEquals(24f, xml.getAttributeFloatValue(namespace, "viewportWidth", 0f), .001f)
                            assertEquals(24f, xml.getAttributeFloatValue(namespace, "viewportHeight", 0f), .001f)
                        } else {
                            assertEquals(1.8f, xml.getAttributeFloatValue(namespace, "strokeWidth", 0f), .001f)
                            assertEquals(1, xml.getAttributeIntValue(namespace, "strokeLineCap", -1))
                            contours.add(xml.getAttributeValue(namespace, "pathData"))
                        }
                    }
                    xml.next()
                }
            }
        }
        assertEquals(icons.size, contours.size)
        val sheet = Bitmap.createBitmap(480, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.WHITE)
        icons.forEachIndexed { index, id ->
            val drawable = checkNotNull(context.getDrawable(id))
            val left = index % 5 * 96 + 20
            val top = index / 5 * 96 + 20
            drawable.setBounds(left, top, left + 56, top + 56)
            drawable.draw(canvas)
        }
        val directory = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(directory, "ui-icons.png").outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
