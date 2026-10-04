package com.vnventory.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.vnventory.app.data.backup.BackupData
import com.vnventory.app.BuildConfig
import com.vnventory.app.ui.components.DatePickerModal
import com.vnventory.app.ui.settings.BackupImportDialogs
import com.vnventory.app.ui.settings.BackupUiState
import com.vnventory.app.ui.settings.SettingsDataContent
import com.vnventory.app.ui.settings.SettingsHomeContent
import com.vnventory.app.ui.settings.SettingsAboutScreen
import com.vnventory.app.ui.theme.VNventoryTheme
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w393dp-h852dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAndDateUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(fontScale: Float = 1f, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VNventoryTheme(darkTheme = dark) { Surface { Box(Modifier.fillMaxSize()) { content() } } }
            }
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val directory = File(checkNotNull(System.getProperty("vnventory.screenshot.dir"))).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun `设置分组入口显示当前货币且全部可点击`() {
        var preferences = 0
        var data = 0
        var about = 0
        show { SettingsHomeContent("JPY", { preferences++ }, { data++ }, { about++ }) }
        compose.onNodeWithText("外观、价格展示与默认货币 · JPY").assertIsDisplayed()
        compose.onNodeWithText("偏好设置").performClick()
        compose.onNodeWithText("备份与恢复").performClick()
        compose.onNodeWithText("关于与数据来源").performScrollTo().performClick()
        assertEquals(1, preferences)
        assertEquals(1, data)
        assertEquals(1, about)
        capture("settings-home")
    }

    @Test fun `大字体深色下备份按钮隐私说明可达且保存中禁止重复操作`() {
        var exports = 0
        var imports = 0
        val state = mutableStateOf(BackupUiState())
        show(fontScale = 1.5f, dark = true) { SettingsDataContent(state.value, { exports++ }, { imports++ }) }
        // 卡片标题和按钮同名，只选择带点击语义的按钮。
        compose.onNode(androidx.compose.ui.test.hasText("导出备份") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("选择备份文件").performScrollTo().performClick()
        assertEquals(1, exports)
        assertEquals(1, imports)
        compose.onNodeWithText("备份为未加密文本", substring = true).performScrollTo().assertIsDisplayed()
        capture("settings-backup-large-dark")
        compose.runOnIdle { state.value = BackupUiState(busy = true, progress = message(MessageKey.BACKUP_PROGRESS_EXPORT)) }
        compose.onNodeWithText("选择备份文件").performScrollTo().assertIsNotEnabled()
        compose.onNode(androidx.compose.ui.test.hasText("导出备份") and androidx.compose.ui.test.hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    @Test fun `关于页显示新图标版本与隐私说明并可返回`() {
        var returned = false
        show { SettingsAboutScreen(onBack = { returned = true }) }
        compose.onNodeWithContentDescription("VNventory 应用图标").assertIsDisplayed()
        compose.onNodeWithText("VNventory", substring = false).assertIsDisplayed()
        compose.onNodeWithText("版本 ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        assertLogoPixels()
        capture("settings-about-light")
        compose.onNodeWithText("收藏数据和配置保存在本机", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("about-content").performScrollToIndex(3)
        compose.onNodeWithText("开源许可 · MPL-2.0").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        assertTrue(returned)
    }

    @Test fun `关于页深色大字体不染色图标且数据来源和许可可达`() {
        show(fontScale = 1.5f, dark = true) { SettingsAboutScreen(onBack = {}) }
        compose.onNodeWithContentDescription("VNventory 应用图标").assertIsDisplayed()
        compose.onNodeWithText("版本 ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        assertLogoPixels()
        capture("settings-about-large-dark")
        compose.onNodeWithText("本应用与 VNDB 官方无隶属关系", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("about-content").performScrollToIndex(3)
        compose.onNodeWithText("开源许可 · MPL-2.0").assertIsDisplayed()
        capture("settings-about-large-dark-footer")
    }

    private fun assertLogoPixels() {
        val logo = compose.onNodeWithContentDescription("VNventory 应用图标").captureToImage().asAndroidBitmap()
        val sleeve = logo.getPixel(logo.width * 650 / 1024, logo.height * 500 / 1024)
        assertTrue("收集 V 的浅色盒套应实际绘制：${Integer.toHexString(sleeve)}",
            Color.red(sleeve) >= 225 && Color.green(sleeve) >= 215 && Color.blue(sleeve) >= 220)
    }

    @Test fun `覆盖先预览再二次确认且失败可见`() {
        val backup = BackupData(0, "JPY", emptyList(), emptyList(), emptyList(), emptyList())
        val state = mutableStateOf(BackupUiState(pendingImport = backup))
        var replaced = 0
        show {
            BackupImportDialogs(
                state.value, { state.value = state.value.copy(restoreCurrency = it) },
                { state.value = state.value.copy(pendingImport = null) }, {},
                { state.value = state.value.copy(replaceConfirmation = true) },
                { state.value = state.value.copy(replaceConfirmation = false) }, { replaced++ },
                error = Message.Literal("测试恢复失败"),
            )
        }
        compose.onNodeWithText("检查备份").assertIsDisplayed()
        compose.onNodeWithText("同时恢复默认货币（JPY）").assertIsDisplayed()
        compose.onNodeWithText("覆盖恢复").performClick()
        assertEquals(0, replaced)
        compose.onNodeWithText("覆盖现有数据？").assertIsDisplayed()
        compose.onNodeWithText("测试恢复失败").assertIsDisplayed()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("检查备份").assertIsDisplayed()
        compose.onNodeWithText("覆盖恢复").performClick()
        compose.onNodeWithText("确认覆盖").performClick()
        assertEquals(1, replaced)
    }

    @Test fun `日期选择器切换输入和日历保留修改后的日期`() {
        var selected: LocalDate? = null
        var dismissed = false
        show { DatePickerModal(LocalDate.of(2026, 10, 3), { dismissed = true }, { selected = it }) }
        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNode(hasSetTextAction()).performTextReplacement("10152026")
        compose.onNodeWithContentDescription("Switch to calendar input mode").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("确定").performClick()
        assertEquals(LocalDate.of(2026, 10, 15), selected)
        assertTrue(dismissed)
    }

    @Test fun `历史日期初始化到正确月份取消不提交选择`() {
        var confirmed = false
        var dismissed = false
        show { DatePickerModal(LocalDate.of(1999, 5, 31), { dismissed = true }, { confirmed = true }) }
        compose.onNodeWithText("May 1999").assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch to text input mode").performClick()
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertFalse(confirmed)
    }
}
