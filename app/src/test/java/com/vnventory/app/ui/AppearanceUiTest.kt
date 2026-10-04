package com.vnventory.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.domain.model.AppearancePreferences
import com.vnventory.app.domain.model.ThemeMode
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.settings.AppearancePreferencesContent
import com.vnventory.app.ui.theme.PlumDarkPrimary
import com.vnventory.app.ui.theme.PlumPrimary
import com.vnventory.app.ui.theme.VNventoryTheme
import com.vnventory.app.ui.theme.appearanceColorScheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val directory = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `三段主题与动态取色独立切换并立即改变实际配色`() {
        val appearance = mutableStateOf(AppearancePreferences())
        var primary = Color.Unspecified
        compose.setContent {
            VNventoryTheme(appearance.value) {
                val color = MaterialTheme.colorScheme.primary
                SideEffect { primary = color }
                Surface {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        SectionCard {
                            AppearancePreferencesContent(appearance.value,
                                { appearance.value = appearance.value.copy(themeMode = it) },
                                { appearance.value = appearance.value.copy(dynamicColor = it) })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("跟随系统").assertIsSelected()
        compose.runOnIdle { assertEquals(PlumPrimary, primary) }
        compose.onNodeWithText("深色").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(PlumDarkPrimary, primary) }
        capture("appearance-fixed-dark")
        compose.onNode(hasText("动态取色") and isToggleable()).performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(ThemeMode.DARK, appearance.value.themeMode)
            assertEquals(dynamicDarkColorScheme(context).primary, primary)
        }
        compose.onNodeWithText("浅色").performClick().assertIsSelected()
        compose.runOnIdle {
            assertTrue(appearance.value.dynamicColor)
            assertEquals(dynamicLightColorScheme(context).primary, primary)
        }
        capture("appearance-dynamic-light")
        compose.onNode(hasText("动态取色") and isToggleable()).performClick().assertIsOff()
        compose.runOnIdle {
            assertEquals(ThemeMode.LIGHT, appearance.value.themeMode)
            assertEquals(PlumPrimary, primary)
        }
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-port-night-xxhdpi")
    fun `系统深色时可强制浅色且大字体三段选择不裁掉文字`() {
        val appearance = mutableStateOf(AppearancePreferences())
        var primary = Color.Unspecified
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                VNventoryTheme(appearance.value) {
                    val color = MaterialTheme.colorScheme.primary
                    SideEffect { primary = color }
                    Surface {
                        SectionCard(Modifier.padding(24.dp)) {
                            AppearancePreferencesContent(appearance.value,
                                { appearance.value = appearance.value.copy(themeMode = it) },
                                { appearance.value = appearance.value.copy(dynamicColor = it) })
                        }
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(PlumDarkPrimary, primary) }
        compose.onNodeWithText("浅色").assertIsDisplayed().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(PlumPrimary, primary) }
        compose.onNodeWithText("跟随系统").assertIsDisplayed().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(PlumDarkPrimary, primary) }
        compose.onNodeWithText("深色").assertIsDisplayed()
        val heights = listOf("跟随系统", "浅色", "深色").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.height }
        assertTrue("Three segments must share one height: $heights", heights.max() - heights.min() < 1f)
        capture("appearance-large-dark")
    }

    @Test fun `旧Android动态取色禁用且配色安全回退`() {
        compose.setContent {
            VNventoryTheme {
                SectionCard(Modifier.padding(24.dp)) {
                    AppearancePreferencesContent(AppearancePreferences(), {}, {}, dynamicColorSupported = false)
                }
            }
        }
        compose.onNode(hasText("动态取色") and isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("需要 Android 12", substring = true).assertIsDisplayed()
        assertEquals(PlumPrimary, appearanceColorScheme(context, false, true, sdkInt = 30).primary)
        assertEquals(PlumDarkPrimary, appearanceColorScheme(context, true, true, sdkInt = 30).primary)
    }
}
