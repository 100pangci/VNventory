package com.vnventory.app.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vnventory.app.data.repository.BudgetOverview
import com.vnventory.app.domain.cost.BudgetIssue
import com.vnventory.app.domain.cost.BudgetIssueReason
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.cost.BudgetSummary
import com.vnventory.app.domain.model.BudgetPreferences
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.budget.BudgetDetailContent
import com.vnventory.app.ui.budget.BudgetDetailUiState
import com.vnventory.app.ui.home.HomeBudget
import com.vnventory.app.ui.home.HomeContent
import com.vnventory.app.ui.home.HomeStats
import com.vnventory.app.ui.settings.BudgetBackfillUiState
import com.vnventory.app.ui.settings.SettingsBudgetContent
import com.vnventory.app.ui.settings.parseBudgetYear
import com.vnventory.app.ui.theme.VNventoryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class BudgetUiTest {
    @get:Rule val compose = createComposeRule()

    private fun copy(id: Long, price: Long?, currency: String, date: LocalDate? = LocalDate.of(2026, 1, 10)) = OwnedCopy(
        id = id, vnId = "v1", releaseId = null, vnTitle = "サクラノ詩", releaseTitle = "初回版", coverUrl = null,
        priceMinor = price, currency = currency, condition = CopyCondition.USED, conditionNote = null,
        purchaseDate = date, shop = null, orderId = null, notes = null, createdAt = 0, updatedAt = 0,
    )

    private fun overview() = BudgetOverview(
        preferences = BudgetPreferences(enabled = true, currency = "CNY", yearlyAmounts = mapOf(2025 to 100_000L), includeFees = true),
        targetCurrency = "CNY",
        summary = BudgetSummary(
            "CNY",
            mapOf(2026 to 128_000L),
            listOf(
                BudgetIssue(BudgetRecordKind.COPY, 1, BudgetIssueReason.NOT_FROZEN, null, LocalDate.of(2026, 1, 10)),
                BudgetIssue(BudgetRecordKind.COPY, 2, BudgetIssueReason.NO_DATE, null, null),
            ),
        ),
        copies = mapOf(1L to copy(1, 12_800L, "JPY"), 2L to copy(2, 6_800L, "JPY", date = null)),
        expenses = emptyMap(),
        orderTitles = emptyMap(),
        currentYear = 2026,
    )

    @Test fun `首页预算卡按开关显示并展示进度与未计入`() {
        val budget = mutableStateOf<HomeBudget?>(null)
        var opened = false
        compose.setContent {
            VNventoryTheme {
                Surface {
                    HomeContent(HomeStats(), emptyList(), {}, {}, {}, budget = budget.value, onBudgetClick = { opened = true })
                }
            }
        }
        compose.onNodeWithText("2026 年度预算").assertDoesNotExist()
        compose.runOnIdle { budget.value = HomeBudget(true, 2026, 128_000L, 500_000L, "CNY", 2) }
        compose.onNodeWithText("2026 年度预算").assertIsDisplayed()
        compose.onNodeWithText(Money.format(128_000L, "CNY")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 笔未计入").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("查看年度预算").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun `预算设置页开关逐年金额与补全入口`() {
        val enabled = mutableStateOf(false)
        var editedYear: Int? = null
        var backfilled = false
        var addYearRequested = false
        val settingsOverview = mutableStateOf(overview().let { it.copy(preferences = it.preferences.copy(enabled = false)) })
        compose.setContent {
            VNventoryTheme {
                SettingsBudgetContent(
                    overview = settingsOverview.value,
                    backfill = BudgetBackfillUiState(),
                    onEnabled = { enabled.value = it; settingsOverview.value = settingsOverview.value.let { current -> current.copy(preferences = current.preferences.copy(enabled = it)) } },
                    onCurrency = {},
                    onIncludeFees = {},
                    onEditYear = { editedYear = it },
                    onAddYear = { addYearRequested = true },
                    onBackfill = { backfilled = true },
                )
            }
        }
        compose.onAllNodes(isToggleable())[0].assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(enabled.value) }
        // 2026 有折算合计但尚未设置金额，点击进入逐年金额编辑。
        compose.onNodeWithText("2026 年").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2026 年").performClick()
        assertEquals(2026, editedYear)
        compose.onNodeWithText("添加其他年份").performScrollTo().performClick()
        assertTrue(addYearRequested)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("待补全 1 笔"))
        compose.onNodeWithText("待补全 1 笔").assertIsDisplayed()
        compose.onNodeWithText("立即补全").performClick()
        assertTrue(backfilled)
    }

    @Test fun `预算年份可选择常见范围内任意年份并拒绝越界`() {
        assertEquals(2042, parseBudgetYear("2042"))
        assertEquals(1, parseBudgetYear("1"))
        assertEquals(9999, parseBudgetYear("9999"))
        assertNull(parseBudgetYear("0"))
        assertNull(parseBudgetYear("10000"))
        assertNull(parseBudgetYear("20x2"))
    }

    @Test fun `预算详情展示当年进度与未计入操作`() {
        val state = BudgetDetailUiState(overview = overview(), selectedYear = 2026)
        var retried: Pair<BudgetIssue, Boolean>? = null
        var manual: BudgetIssue? = null
        var fixed: BudgetIssue? = null
        compose.setContent {
            VNventoryTheme {
                BudgetDetailContent(
                    state = state,
                    onSelectYear = {},
                    onBackfill = {},
                    onRetry = { issue, stale -> retried = issue to stale },
                    onManual = { manual = it },
                    onFix = { fixed = it },
                )
            }
        }
        compose.onNodeWithText("已折算花费").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Money.format(128_000L, "CNY")).assertIsDisplayed()
        compose.onNodeWithText("未定格汇率（1）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重新获取").performScrollTo().performClick()
        assertEquals(false, retried!!.second)
        compose.onNodeWithText("手动填写").performScrollTo().performClick()
        assertEquals(1L, manual!!.id)
        compose.onNodeWithText("缺少日期（1）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("去补填").performScrollTo().performClick()
        assertEquals(2L, fixed!!.id)
    }

    @Test fun `预算详情没有未计入时保持安静`() {
        val clean = overview().copy(summary = BudgetSummary("CNY", mapOf(2026 to 128_000L), emptyList()))
        compose.setContent {
            VNventoryTheme { BudgetDetailContent(BudgetDetailUiState(overview = clean), {}, {}, { _, _ -> }, {}, {}) }
        }
        compose.onNodeWithText("没有未计入的记录").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("立即补全").assertDoesNotExist()
    }

    @Test fun `编辑收藏外币时显示定格状态与操作`() {
        var manual = false
        var cleared = false
        var retried = false
        val frozen = copy(1, 12_800L, "JPY").copy(
            fxRateScaled = 42_299_852L, fxDate = LocalDate.of(2026, 1, 10), fxToCurrency = "CNY", fxSource = "currency-api",
        )
        val state = com.vnventory.app.ui.edit.CopyEditUiState(
            loading = false,
            copy = frozen,
            form = com.vnventory.app.ui.edit.EditFormState(releaseTitle = "Manual", priceText = "12800", currency = "JPY"),
            targetCurrency = "CNY",
        )
        compose.setContent {
            VNventoryTheme {
                Surface {
                    com.vnventory.app.ui.edit.CopyEditContent(
                        state, {}, {}, onRetryFx = { retried = true }, onManualFx = { manual = true }, onClearFx = { cleared = true },
                    )
                }
            }
        }
        compose.onNodeWithText("汇率定格").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("已定格：1 JPY = 0.0423 CNY · 汇率日期 2026-01-10 · 来源 currency-api", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重新获取").performScrollTo().performClick()
        assertTrue(retried)
        compose.onNodeWithText("手动填写").performScrollTo().performClick()
        assertTrue(manual)
        compose.onNodeWithText("清除定格").performScrollTo().performClick()
        assertTrue(cleared)
    }
}
