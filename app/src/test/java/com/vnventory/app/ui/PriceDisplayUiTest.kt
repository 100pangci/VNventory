package com.vnventory.app.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.SpendingDisplayPreferences
import com.vnventory.app.ui.collection.CollectionContent
import com.vnventory.app.ui.collection.CollectionUiState
import com.vnventory.app.ui.components.OwnedCoverCard
import com.vnventory.app.ui.components.OwnedListCard
import com.vnventory.app.ui.detail.CopyDetailContent
import com.vnventory.app.ui.detail.CopyDetailUiState
import com.vnventory.app.ui.home.HomeContent
import com.vnventory.app.ui.home.HomeStats
import com.vnventory.app.ui.orders.UnallocatedFees
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.theme.VNventoryTheme
import com.vnventory.app.ui.settings.PriceDisplayPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class PriceDisplayUiTest {
    @get:Rule val compose = createComposeRule()
    private val copy = ShelfPreviewData.copies.first()

    @Test fun `偏好开关独立且消费币种选择折叠`() {
        val shelf = mutableStateOf(false)
        val stats = mutableStateOf(false)
        val spending = mutableStateOf(SpendingDisplayPreferences())
        compose.setContent {
            VNventoryTheme {
                PriceDisplayPreferences(
                    shelf.value, stats.value, { shelf.value = it }, { stats.value = it },
                    spendingDisplay = spending.value,
                    defaultCurrency = "JPY",
                    onUnifiedSpendingCurrency = { spending.value = spending.value.copy(unifiedCurrency = it) },
                    onSpendingCurrency = { spending.value = spending.value.copy(currency = it) },
                )
            }
        }
        compose.onAllNodes(isToggleable())[0].assertIsOff().performClick().assertIsOn()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
        compose.runOnIdle { assertTrue(shelf.value); assertFalse(stats.value) }
        compose.onAllNodes(isToggleable())[1].performClick().assertIsOn()
        compose.runOnIdle { assertTrue(shelf.value); assertTrue(stats.value) }
        compose.onAllNodes(isToggleable())[2].performClick().assertIsOn()
        compose.onNodeWithText("USD（$）").assertDoesNotExist()
        compose.onNodeWithText("更改").performClick()
        compose.onNodeWithText("CNY（¥）").performClick()
        compose.onNodeWithText("当前币种：CNY（¥）").assertIsDisplayed()
        compose.runOnIdle { assertTrue(spending.value.unifiedCurrency); assertEquals("CNY", spending.value.currency) }
    }

    @Test fun `书架默认隐藏金额单盒编号与标题副标题`() {
        compose.setContent { VNventoryTheme { Surface {
            CollectionContent(CollectionUiState(copies = listOf(copy), loading = false), {}, {}, {}, {})
        } } }
        compose.onNodeWithText("我的书架").assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(6800, "JPY")).assertDoesNotExist()
        compose.onNodeWithText("第 1 盒").assertDoesNotExist()
        compose.onNodeWithText("数据来自本地记录", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 盒实体", substring = true).assertDoesNotExist()
    }

    @Test fun `开启封面卡片价格显示真实零价`() {
        compose.setContent { VNventoryTheme { OwnedCoverCard(copy.copy(priceMinor = 0), {}, showPrice = true) } }
        compose.onNodeWithText(Money.formatWithCode(0, "JPY")).assertIsDisplayed()
        compose.onNodeWithText("未记录").assertDoesNotExist()
    }

    @Test fun `开启列表价格显示已知金额`() {
        compose.setContent { VNventoryTheme { OwnedListCard(copy, {}, showPrice = true) } }
        compose.onNodeWithText(Money.formatWithCode(6800, "JPY")).assertIsDisplayed()
    }

    @Test fun `开启价格仍不把未知金额显示为零`() {
        compose.setContent { VNventoryTheme { OwnedCoverCard(copy.copy(priceMinor = null), {}, showPrice = true) } }
        compose.onNodeWithText("未记录").assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(0, "JPY")).assertDoesNotExist()
    }

    @Test fun `详情即使默认关闭价格仍显示未知价格状态`() {
        compose.setContent { VNventoryTheme { CopyDetailContent(CopyDetailUiState(loading = false, copy = copy.copy(priceMinor = null)), {}) } }
        compose.onNodeWithText("未记录").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(0, "JPY")).assertDoesNotExist()
        compose.onNodeWithText("盒 #", substring = true).assertDoesNotExist()
    }

    @Test fun `首页价格统计默认不存在`() {
        compose.setContent { VNventoryTheme { HomeContent(HomeStats(), emptyList(), {}, {}, {}) } }
        compose.onNodeWithText("已记录的支出（含附加费用）").assertDoesNotExist()
    }

    @Test fun `开启统计显示明确价格覆盖率`() {
        compose.setContent { VNventoryTheme { HomeContent(HomeStats(copyCount = 8, pricedCopyCount = 6, priceTotals = mapOf("CNY" to 128000L), showPriceStats = true), emptyList(), {}, {}, {}) } }
        compose.onNodeWithText("6/8 盒有价格", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun `统一币种时提示无法折算的消费`() {
        compose.setContent {
            VNventoryTheme {
                HomeContent(
                    HomeStats(
                        copyCount = 2,
                        pricedCopyCount = 1,
                        priceTotals = mapOf("CNY" to 12_800L),
                        showPriceStats = true,
                        unifiedSpendingCurrency = true,
                        unconvertedSpendingCount = 3,
                    ),
                    emptyList(), {}, {}, {},
                )
            }
        }
        compose.onNodeWithText("有 3 笔记录缺少匹配的定格汇率，未计入统一合计。", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun `没有未分摊费用正常状态保持安静`() {
        compose.setContent { VNventoryTheme { UnallocatedFees(emptyMap()) } }
        compose.onNodeWithText("未分摊费用", substring = true).assertDoesNotExist()
        compose.onNodeWithText("无").assertDoesNotExist()
    }

    @Test fun `真实未分摊费用仍提示准确金额`() {
        compose.setContent { VNventoryTheme { UnallocatedFees(mapOf("JPY" to 120L)) } }
        compose.onNodeWithText("未分摊费用", substring = true).assertIsDisplayed()
        compose.onNodeWithText(Money.formatWithCode(120, "JPY")).assertIsDisplayed()
    }
}
