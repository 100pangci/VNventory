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

    @Test fun `收集V原生矢量保留浅色V薰衣草盒套与香槟金收藏牌`() {
        val foreground = render(drawable(R.drawable.ic_launcher_foreground))
        assertEquals(0, Color.alpha(foreground.getPixel(100, 100)))
        val ivoryV = foreground.allPixels().count { Color.red(it) > 230 && Color.green(it) > 220 && Color.blue(it) > 225 }
        val goldTab = foreground.allPixels().count { Color.red(it) > 180 && Color.red(it) > Color.green(it) && Color.green(it) > Color.blue(it) }
        val lavender = foreground.allPixels().count { Color.blue(it) > Color.red(it) && Color.red(it) > Color.green(it) }
        assertTrue("The ivory V is retained", ivoryV > 1_000)
        assertTrue("The champagne-gold collection tab is retained", goldTab > 100)
        assertTrue("The lavender sleeve palette is retained", lavender > 5_000)
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
        val icon = normal as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        val launcherForeground = render(icon.foreground)
        val sourceForeground = render(drawable(R.drawable.ic_launcher_foreground_safe))
        val fullForeground = render(drawable(R.drawable.ic_launcher_foreground))
        assertTrue("Launcher uses the generated safe-zone vector", launcherForeground.sameAs(sourceForeground))
        assertTrue("Safe-zone art is inset relative to the full wordmark", sourceForeground.alphaBounds().width() < fullForeground.alphaBounds().width())
        val background = render(normal.background)
        assertEquals(255, Color.alpha(background.getPixel(0, 0)))
        assertEquals(255, Color.alpha(background.getPixel(1023, 1023)))
        assertTrue(render(normal).sameAs(render(round)))
        val visibleForegroundWidth = sourceForeground.alphaBounds().width().toFloat() * icon.foreground.bounds.width() / 1024f
        assertTrue("Launcher foreground leaves comfortable horizontal margins", visibleForegroundWidth <= 1024f * .72f)
        capture("brand-launcher", render(normal))
    }

    @Test fun `单色主题图标V与价签孔镂空不是一整块方形`() {
        val monochrome = render(drawable(R.drawable.ic_launcher_monochrome))
        assertEquals(0, Color.alpha(monochrome.getPixel(100, 100)))
        assertEquals("Spine label remains punched out", 0, Color.alpha(monochrome.getPixel(353, 375)))
        assertEquals("Collection-tab eyelet remains punched out", 0, Color.alpha(monochrome.getPixel(629, 319)))
        assertEquals("The game sleeve is a foreground silhouette", 255, Color.alpha(monochrome.getPixel(411, 507)))
        capture("brand-monochrome", monochrome)
    }

    private fun Bitmap.allPixels(): Sequence<Int> = sequence {
        for (y in 0 until height) for (x in 0 until width) yield(getPixel(x, y))
    }

    private fun Bitmap.alphaBounds(): android.graphics.Rect {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (Color.alpha(getPixel(x, y)) == 0) continue
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
        return android.graphics.Rect(left, top, right + 1, bottom + 1)
    }
}
