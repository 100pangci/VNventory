package com.vnventory.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.ui.add.AddFlowUiState
import com.vnventory.app.ui.add.PurchaseFormContent
import com.vnventory.app.ui.add.PurchaseFormState
import com.vnventory.app.ui.components.ShopChannelField
import com.vnventory.app.ui.settings.SettingsShopsContent
import com.vnventory.app.ui.settings.ShopEditorDialog
import com.vnventory.app.ui.settings.ShopEditorState
import com.vnventory.app.ui.theme.VNventoryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w393dp-h852dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShopChannelUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `下拉选择常用店铺并保留临时输入与清空能力`() {
        val name = mutableStateOf("旧店铺")
        val shops = mutableStateOf(listOf("駿河屋", "メルカリ"))
        compose.setContent { VNventoryTheme { Surface { ShopChannelField(name.value, { name.value = it }, shops.value) } } }
        compose.onNodeWithText("旧店铺").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择店铺/渠道").performClick()
        compose.onNodeWithText("メルカリ").performClick()
        compose.runOnIdle { assertEquals("メルカリ", name.value); shops.value = emptyList() }
        compose.onNodeWithText("メルカリ").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("一次性渠道")
        compose.runOnIdle { assertEquals("一次性渠道", name.value); assertTrue(shops.value.isEmpty()) }
        compose.onNodeWithContentDescription("选择店铺/渠道").performClick()
        compose.onNodeWithText("不填写店铺/渠道").performClick()
        compose.runOnIdle { assertEquals("", name.value) }
    }

    @Test fun `设置店铺列表新增编辑删除入口可点击`() {
        var added = 0
        var edited: String? = null
        var deleted: String? = null
        compose.setContent {
            VNventoryTheme { Surface { SettingsShopsContent(listOf("駿河屋"), { added++ }, { edited = it }, { deleted = it }) } }
        }
        compose.onNodeWithText("新增店铺/渠道").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle {
            assertEquals(1, added)
            assertEquals("駿河屋", edited)
            assertEquals("駿河屋", deleted)
        }
    }

    @Test fun `新增店铺对话框空名不可保存忙碌时不可重复操作`() {
        val editor = mutableStateOf(ShopEditorState(open = true))
        val busy = mutableStateOf(false)
        var saved = 0
        compose.setContent {
            VNventoryTheme {
                Surface {
                    Box(Modifier.fillMaxSize()) {
                        ShopEditorDialog(editor.value, busy.value, { editor.value = editor.value.copy(name = it) }, { saved++ }, {})
                    }
                }
            }
        }
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("駿河屋")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(1, saved); busy.value = true }
        compose.onNodeWithText("正在保存…").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
    }

    @Test fun `购入表单大字体下可从候选项选择店铺`() {
        val form = mutableStateOf(PurchaseFormState(manualVersion = true, releaseTitle = "Manual"))
        val vn = VnInfo("v1", "A", null, null, null, null)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                VNventoryTheme(darkTheme = true) {
                    Surface {
                        Box(Modifier.fillMaxSize()) {
                            PurchaseFormContent(AddFlowUiState(selectedVn = vn, form = form.value, shopChannels = listOf("駿河屋")), { form.value = it })
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("选择店铺/渠道").performScrollTo().performClick()
        compose.onNodeWithText("駿河屋").performClick()
        compose.runOnIdle { assertEquals("駿河屋", form.value.shop) }
    }
}
