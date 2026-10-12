package com.vnventory.app.domain.cost

import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.model.Money
import java.time.LocalDate

/** 预算记录来源；未计入清单需要按来源送回对应编辑入口。 */
enum class BudgetRecordKind { COPY, EXPENSE }

/**
 * 一条参与年度预算汇总的记录（已解析归年日期与定格快照）。
 *
 * [date] 为 null 表示缺日期；[amountMinor] 为 null 表示未记录价格（仅收藏）。
 */
data class BudgetRecordInput(
    val kind: BudgetRecordKind,
    val id: Long,
    val amountMinor: Long?,
    val currency: String,
    val date: LocalDate?,
    val orderId: Long?,
    val fxRateScaled: Long?,
    val fxToCurrency: String?,
)

/** 未计入原因，UI 分类展示与处理入口共用。 */
enum class BudgetIssueReason { NO_PRICE, NO_DATE, NOT_FROZEN, STALE_TARGET }

data class BudgetIssue(
    val kind: BudgetRecordKind,
    val id: Long,
    val reason: BudgetIssueReason,
    val orderId: Long?,
    /** 归年日期；未定格记录手动填写时的默认定格日期。 */
    val date: LocalDate?,
)

/**
 * 年度预算汇总（纯函数结果）。
 *
 * - [totalsByYear]：只包含已折算计入的金额；没有计入记录的年份不出现。
 * - [issues]：不猜测、不补 0 的记录，按原因分类供 UI 展示与补录。
 */
data class BudgetSummary(
    val currency: String,
    val totalsByYear: Map<Int, Long>,
    val issues: List<BudgetIssue>,
) {
    fun totalForYear(year: Int): Long = totalsByYear[year] ?: 0L

    val issuesByReason: Map<BudgetIssueReason, List<BudgetIssue>> = issues.groupBy { it.reason }
}

/** 纯函数预算引擎：把「已记录金额 + 定格汇率」折算到目标币种并按年汇总。 */
object BudgetEngine {

    fun summarize(records: List<BudgetRecordInput>, targetCurrency: String): BudgetSummary {
        val target = Money.normalize(targetCurrency)
        val totals = mutableMapOf<Int, Long>()
        val issues = mutableListOf<BudgetIssue>()
        records.forEach { record ->
            val amount = record.amountMinor
            val year = record.date?.year
            when {
                amount == null -> issues += BudgetIssue(record.kind, record.id, BudgetIssueReason.NO_PRICE, record.orderId, record.date)

                year == null -> issues += BudgetIssue(record.kind, record.id, BudgetIssueReason.NO_DATE, record.orderId, record.date)

                Money.normalize(record.currency) == target ->
                    totals[year] = Money.add(totals[year] ?: 0L, amount)

                record.fxRateScaled == null || record.fxToCurrency == null ->
                    issues += BudgetIssue(record.kind, record.id, BudgetIssueReason.NOT_FROZEN, record.orderId, record.date)

                Money.normalize(record.fxToCurrency) != target ->
                    issues += BudgetIssue(record.kind, record.id, BudgetIssueReason.STALE_TARGET, record.orderId, record.date)

                else -> {
                    val converted = Fx.convert(amount, record.currency, record.fxRateScaled, target)
                    totals[year] = Money.add(totals[year] ?: 0L, converted)
                }
            }
        }
        return BudgetSummary(target, totals.toMap(), issues)
    }
}
