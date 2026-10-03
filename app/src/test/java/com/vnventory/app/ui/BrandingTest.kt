package com.vnventory.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun drawable(id: Int): Drawable = checkNotNull(context.getDrawable(id))

    private fun render(drawable: Drawable): Bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, it.width, it.height)
        drawable.draw(Canvas(it))
    }

    private fun capture(name: String, bitmap: Bitmap) {
        val directory = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `SVG原色前景保留白色V紫色盒面金色价签与透明安全区`() {
        val foreground = render(drawable(R.drawable.ic_launcher_foreground))
        assertEquals(0, Color.alpha(foreground.getPixel(100, 100)))
        val v = foreground.getPixel(448, 420)
        assertTrue(Color.red(v) >= 230 && Color.green(v) >= 225 && Color.blue(v) >= 240)
        val cover = foreground.getPixel(560, 680)
        assertTrue(Color.blue(cover) > Color.red(cover) && Color.red(cover) > Color.green(cover))
        val tag = foreground.getPixel(704, 710)
        assertTrue(Color.red(tag) > 180 && Color.green(tag) > 100 && Color.blue(tag) < 180)
        val logo = render(LayerDrawable(arrayOf(drawable(R.drawable.vnventory_logo_background), drawable(R.drawable.ic_launcher_foreground))))
        assertEquals(0, Color.alpha(logo.getPixel(0, 0)))
        assertEquals(255, Color.alpha(logo.getPixel(512, 100)))
        assertNotEquals(logo.getPixel(512, 100), logo.getPixel(512, 900))
        capture("brand-logo", logo)
    }

    @Test fun `普通和圆形启动图标统一使用新前景且满铺背景`() {
        val normal = drawable(R.mipmap.ic_launcher)
        val round = drawable(R.mipmap.ic_launcher_round)
        assertTrue(normal is AdaptiveIconDrawable)
        assertTrue(round is AdaptiveIconDrawable)
        assertNotNull((normal as AdaptiveIconDrawable).monochrome)
        val background = render(normal.background)
        assertEquals(255, Color.alpha(background.getPixel(0, 0)))
        assertEquals(255, Color.alpha(background.getPixel(1023, 1023)))
        assertTrue(render(normal).sameAs(render(round)))
        capture("brand-launcher", render(normal))
    }

    @Test fun `单色主题图标V与价签孔镂空不是一整块方形`() {
        val monochrome = render(drawable(R.drawable.ic_launcher_monochrome))
        assertEquals(0, Color.alpha(monochrome.getPixel(100, 100)))
        assertEquals(0, Color.alpha(monochrome.getPixel(448, 420)))
        assertEquals(0, Color.alpha(monochrome.getPixel(652, 651)))
        assertEquals(Color.WHITE, monochrome.getPixel(560, 680))
        capture("brand-monochrome", monochrome)
    }
}
