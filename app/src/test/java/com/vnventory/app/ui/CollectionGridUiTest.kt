package com.vnventory.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.collection.CollectionContent
import com.vnventory.app.ui.collection.CollectionUiState
import com.vnventory.app.ui.components.OwnedListCard
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 我的书架网格卡片的信息分层；列表视图的现状由这里的守卫用例与 UiPolishUiTest 共同锁定。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class CollectionGridUiTest {
    @get:Rule val compose = createComposeRule()

    private val usedSingle = ShelfPreviewData.copies.first().copy(condition = CopyCondition.USED)

    private fun showCollection(copies: List<OwnedCopy>, showPrices: Boolean = false, showReleaseNames: Boolean = false, allCopies: List<OwnedCopy> = copies) {
        compose.setContent {
            VNventoryTheme {
                Surface {
                    CollectionContent(
                        CollectionUiState(copies = copies, loading = false, showPrices = showPrices, showReleaseNames = showReleaseNames, allCopies = allCopies),
                        {}, {}, {}, {},
                    )
                }
            }
        }
    }

    @Test fun `书架页只保留标题不再显示 COLLECTION`() {
        showCollection(listOf(usedSingle))
        compose.onNodeWithText("我的书架").assertIsDisplayed()
        compose.onNodeWithText("COLLECTION").assertDoesNotExist()
        compose.onNodeWithText("VNVENTORY").assertDoesNotExist()
    }

    @Test fun `网格单盒只显示品相且不显示第 1 盒`() {
        showCollection(listOf(usedSingle))
        compose.onNodeWithText("中古").assertIsDisplayed()
        compose.onNodeWithText("第 1 盒").assertDoesNotExist()
        // 品相标签只应存在于封面叠加层，正文不再有独立的品相标签。
        compose.onAllNodesWithText("中古", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("cover-condition-number", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `网格手动版本标签移到封面左上角`() {
        val manual = ShelfPreviewData.copies.last()
        showCollection(listOf(manual))
        compose.onNodeWithTag("cover-manual", useUnmergedTree = true).assertIsDisplayed()
        // “手动”只应存在于封面叠加层，正文不再有独立标签。
        compose.onAllNodesWithText("手动", useUnmergedTree = true).assertCountEquals(1)
        val cover = compose.onNodeWithContentDescription(manual.vnTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tag = compose.onNodeWithTag("cover-manual", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(tag.top >= cover.top - 1f && tag.bottom <= cover.bottom + 1f)
        assertTrue(tag.left >= cover.left - 1f && tag.right <= cover.right + 1f)
        assertTrue("manual tag should sit on the upper left", tag.top < cover.top + cover.height / 2 && tag.left < cover.left + cover.width / 2)
    }

    @Test fun `网格多盒合并显示品相与盒号`() {
        val second = usedSingle.copy(id = 102, createdAt = 1)
        showCollection(listOf(usedSingle, second))
        compose.onNodeWithText("中古 · 第 1 盒").assertIsDisplayed()
        compose.onNodeWithText("中古 · 第 2 盒").assertIsDisplayed()
        // 不再单独显示品相或“第 X 盒”。
        compose.onNodeWithText("中古").assertDoesNotExist()
        compose.onNodeWithText("第 2 盒").assertDoesNotExist()
    }

    @Test fun `网格搜索过滤后盒号仍按完整收藏计算`() {
        val second = usedSingle.copy(id = 102, createdAt = 1)
        showCollection(copies = listOf(second), allCopies = listOf(usedSingle, second))
        compose.onNodeWithText("中古 · 第 2 盒").assertIsDisplayed()
        compose.onNodeWithText("第 1 盒").assertDoesNotExist()
    }

    @Test fun `网格标题保持单行且长标题省略`() {
        val longTitle = usedSingle.vnTitle + " 完全版限定收藏套装特别篇"
        showCollection(listOf(usedSingle.copy(vnTitle = longTitle)))
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longTitle, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().hasVisualOverflow)
    }

    @Test fun `网格默认隐藏版本名开关开启后显示`() {
        val releaseTitle = usedSingle.releaseTitle!!
        val state = mutableStateOf(CollectionUiState(copies = listOf(usedSingle), loading = false))
        compose.setContent { VNventoryTheme { Surface { CollectionContent(state.value, {}, {}, {}, {}) } } }
        compose.onNodeWithText(releaseTitle).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(showReleaseNames = true) }
        compose.onNodeWithText(releaseTitle).assertIsDisplayed()
        // 版本名弱化显示：字号小于标题。
        fun fontSize(text: String): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.fontSize.value
        }
        assertTrue(fontSize(releaseTitle) < fontSize(usedSingle.vnTitle))
    }

    @Test fun `网格日期与品相位于封面叠加层左下与右上`() {
        showCollection(listOf(usedSingle))
        val date = usedSingle.purchaseDate!!.toString()
        compose.onAllNodesWithText(date, useUnmergedTree = true).assertCountEquals(1)
        val cover = compose.onNodeWithContentDescription(usedSingle.vnTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dateBounds = compose.onNodeWithTag("cover-purchase-date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val conditionBounds = compose.onNodeWithTag("cover-condition-number", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // 两个叠加标签都完整落在封面区域内（正文不再有独立日期或品相行）。
        for (bounds in listOf(dateBounds, conditionBounds)) {
            assertTrue(bounds.top >= cover.top - 1f && bounds.bottom <= cover.bottom + 1f)
            assertTrue(bounds.left >= cover.left - 1f && bounds.right <= cover.right + 1f)
        }
        // 日期在左下角，品相在右上角。
        assertTrue("date should sit on the lower half", dateBounds.top > cover.top + cover.height / 2)
        assertTrue("date should start from the left", dateBounds.left < cover.left + cover.width / 2)
        assertTrue("condition should sit on the upper half", conditionBounds.top < cover.top + cover.height / 2)
        assertTrue("condition should end at the right", conditionBounds.right > cover.left + cover.width / 2)
    }

    @Test fun `网格价格固定在封面右下且日期堆叠在其上方`() {
        showCollection(listOf(usedSingle), showPrices = true)
        val cover = compose.onNodeWithContentDescription(usedSingle.vnTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val price = compose.onNodeWithTag("cover-price", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val date = compose.onNodeWithTag("cover-purchase-date", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (bounds in listOf(price, date)) {
            assertTrue(bounds.top >= cover.top - 1f && bounds.bottom <= cover.bottom + 1f)
            assertTrue(bounds.left >= cover.left - 1f && bounds.right <= cover.right + 1f)
        }
        assertTrue("price should sit at the bottom right", price.top > cover.top + cover.height / 2 && price.right > cover.left + cover.width / 2)
        // 封面宽度放不下日期与价格并排，日期改为同列堆叠在价格上方，不互相遮挡。
        assertTrue("date should stack above price", date.bottom <= price.top)
        assertTrue("date should align to the right column", date.right > cover.left + cover.width / 2)
    }

    @Test fun `网格没有购买日期时不显示日期标签`() {
        showCollection(listOf(usedSingle.copy(purchaseDate = null)))
        compose.onNodeWithTag("cover-purchase-date", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `网格价格仍跟随书架价格开关且未知金额不变`() {
        val state = mutableStateOf(CollectionUiState(copies = listOf(usedSingle), loading = false, showPrices = false))
        compose.setContent { VNventoryTheme { Surface { CollectionContent(state.value, {}, {}, {}, {}) } } }
        val amount = Money.formatWithCode(usedSingle.priceMinor!!, usedSingle.currency)
        compose.onNodeWithText(amount).assertDoesNotExist()
        compose.onNodeWithTag("cover-price", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(showPrices = true) }
        compose.onNodeWithText(amount).assertIsDisplayed()
        compose.onNodeWithTag("cover-price", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(copies = listOf(usedSingle.copy(priceMinor = null))) }
        compose.waitForIdle()
        compose.onNodeWithText("未记录").assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(0, usedSingle.currency)).assertDoesNotExist()
    }

    @Test fun `详细列表按开关显示版本名并保留品相盒号日期手动与价格`() {
        val manual = ShelfPreviewData.copies.last()
        compose.setContent {
            VNventoryTheme {
                Surface {
                    Column {
                        OwnedListCard(usedSingle, {}, showPrice = true, ordinal = 6, showReleaseName = true)
                        OwnedListCard(manual, {}, showPrice = true)
                    }
                }
            }
        }
        compose.onNodeWithText(usedSingle.vnTitle).assertIsDisplayed()
        // 开启开关时显示版本名，默认关闭的手动版本不显示。
        compose.onNodeWithText(usedSingle.releaseTitle!!).assertIsDisplayed()
        compose.onNodeWithText(manual.releaseTitle!!).assertDoesNotExist()
        // 列表继续分开显示品相与盒号，并补充手动与购买日期。
        compose.onNodeWithText("中古").assertIsDisplayed()
        compose.onNodeWithText("第 6 盒").assertIsDisplayed()
        compose.onNodeWithText(usedSingle.purchaseDate!!.toString()).assertIsDisplayed()
        compose.onNodeWithText("手动").assertIsDisplayed()
        compose.onNodeWithText(manual.purchaseDate!!.toString()).assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(usedSingle.priceMinor!!, usedSingle.currency)).assertIsDisplayed()
    }
}
