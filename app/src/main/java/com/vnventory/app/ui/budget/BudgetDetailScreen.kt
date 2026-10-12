package com.vnventory.app.ui.budget

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.R
import com.vnventory.app.data.repository.BudgetOverview
import com.vnventory.app.data.repository.FxFreezeOutcome
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.cost.BudgetIssue
import com.vnventory.app.domain.cost.BudgetIssueReason
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.FxManualDialog
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.fxOutcomeText
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.SectionHeading
import com.vnventory.app.ui.settings.SettingsDetailScaffold
import com.vnventory.app.ui.text.localized

@Composable
fun BudgetDetailScreen(
    onBack: () -> Unit,
    onCopyClick: (Long) -> Unit,
    onOrderClick: (Long) -> Unit,
    viewModel: BudgetDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var manualTarget by remember { mutableStateOf<BudgetIssue?>(null) }

    SettingsDetailScaffold(stringResource(R.string.budget_detail_title), onBack) { padding ->
        BudgetDetailContent(
            state = state,
            onSelectYear = viewModel::selectYear,
            onBackfill = viewModel::backfill,
            onRetry = { issue, includeStale -> viewModel.retry(issue.kind, issue.id, includeStale) },
            onManual = { manualTarget = it },
            onFix = { issue ->
                when {
                    issue.kind == BudgetRecordKind.COPY -> onCopyClick(issue.id)
                    issue.orderId != null -> onOrderClick(issue.orderId)
                }
            },
            modifier = Modifier.padding(padding),
            error = { OperationError(viewModel) },
        )
    }

    manualTarget?.let { issue ->
        val overview = state.overview
        val copy = overview.copies[issue.id]
        val expense = overview.expenses[issue.id]
        val fromCurrency = copy?.currency ?: expense?.currency ?: overview.targetCurrency
        val snapshotRate = copy?.fxRateScaled ?: expense?.fxRateScaled
        FxManualDialog(
            fromCurrency = fromCurrency,
            toCurrency = overview.targetCurrency,
            initialRateText = snapshotRate?.let(Fx::formatValue).orEmpty(),
            initialDate = issue.date,
            onDismiss = { manualTarget = null },
            onClear = if (snapshotRate != null) {
                { viewModel.clear(issue.kind, issue.id); manualTarget = null }
            } else null,
            onConfirm = { rate, date ->
                viewModel.setManual(issue.kind, issue.id, rate, date)
                manualTarget = null
            },
        )
    }
}

@Composable
internal fun BudgetDetailContent(
    state: BudgetDetailUiState,
    onSelectYear: (Int) -> Unit,
    onBackfill: () -> Unit,
    onRetry: (BudgetIssue, Boolean) -> Unit,
    onManual: (BudgetIssue) -> Unit,
    onFix: (BudgetIssue) -> Unit,
    modifier: Modifier = Modifier,
    error: @Composable () -> Unit = {},
) {
    val overview = state.overview
    val years = remember(overview, state.selectedYear) {
        (overview.summary.totalsByYear.keys + overview.preferences.yearlyAmounts.keys +
            listOf(overview.currentYear, state.selectedYear, overview.currentYear + 1))
            .distinct()
            .sortedDescending()
    }
    val issues = overview.summary.issues
    val pendingCount = issues.count { it.reason == BudgetIssueReason.NOT_FROZEN || it.reason == BudgetIssueReason.STALE_TARGET }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { error() }

        state.lastOutcome?.let { outcome ->
            item {
                Text(
                    text = outcomeText(outcome),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SectionHeading(stringResource(R.string.budget_detail_years), null)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { year ->
                    FilterChip(
                        selected = year == state.selectedYear,
                        onClick = { onSelectYear(year) },
                        label = { Text(stringResource(R.string.budget_year_label, year)) },
                    )
                }
            }
        }

        item { BudgetYearCard(state) }

        item {
            SectionHeading(stringResource(R.string.budget_detail_issues), null)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (issues.isEmpty()) {
                    Text(stringResource(R.string.budget_detail_no_issues), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (pendingCount > 0) {
                        Button(onClick = onBackfill, enabled = !state.backfillBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.budget_backfill_now))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    state.backfillResult?.let {
                        Text(
                            stringResource(R.string.budget_backfill_result, it.succeeded, it.pending, it.unsupported, it.failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    issueGroups(issues).forEach { (reason, group) ->
                        IssueGroup(reason, group, overview, onRetry, onManual, onFix)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetYearCard(state: BudgetDetailUiState) {
    val overview = state.overview
    val currency = overview.targetCurrency
    val spent = overview.summary.totalForYear(state.selectedYear)
    val amount = overview.preferences.yearlyAmounts[state.selectedYear]
    SectionCard {
        Text(stringResource(R.string.budget_year_label, state.selectedYear), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.budget_detail_spent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(spent, currency), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.budget_detail_year_budget), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = amount?.let { Money.format(it, currency) } ?: stringResource(R.string.budget_detail_year_unset),
            style = MaterialTheme.typography.titleMedium,
        )
        if (amount != null && amount > 0) {
            Spacer(Modifier.height(8.dp))
            val fraction = (spent.toDouble() / amount.toDouble()).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            if (spent > amount) {
                Text(
                    stringResource(R.string.budget_detail_over, Money.format(spent - amount, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    stringResource(R.string.budget_detail_remaining, Money.format(amount - spent, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun issueGroups(issues: List<BudgetIssue>): List<Pair<BudgetIssueReason, List<BudgetIssue>>> =
    listOf(
        BudgetIssueReason.NO_PRICE,
        BudgetIssueReason.NO_DATE,
        BudgetIssueReason.NOT_FROZEN,
        BudgetIssueReason.STALE_TARGET,
    ).mapNotNull { reason -> issues.filter { it.reason == reason }.takeIf { it.isNotEmpty() }?.let { reason to it } }

@Composable
private fun IssueGroup(
    reason: BudgetIssueReason,
    issues: List<BudgetIssue>,
    overview: BudgetOverview,
    onRetry: (BudgetIssue, Boolean) -> Unit,
    onManual: (BudgetIssue) -> Unit,
    onFix: (BudgetIssue) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.budget_issue_count, stringResource(reason.labelRes), issues.size),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(stringResource(reason.hintRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        issues.forEach { issue ->
            IssueRow(issue, overview, onRetry, onManual, onFix)
        }
    }
}

@Composable
private fun IssueRow(
    issue: BudgetIssue,
    overview: BudgetOverview,
    onRetry: (BudgetIssue, Boolean) -> Unit,
    onManual: (BudgetIssue) -> Unit,
    onFix: (BudgetIssue) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(issueTitle(issue, overview), style = MaterialTheme.typography.bodyMedium)
        Text(issueAmount(issue, overview), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (issue.reason) {
                BudgetIssueReason.NO_PRICE, BudgetIssueReason.NO_DATE ->
                    TextButton(onClick = { onFix(issue) }) { Text(stringResource(R.string.budget_issue_fix)) }

                BudgetIssueReason.NOT_FROZEN -> {
                    TextButton(onClick = { onRetry(issue, false) }) { Text(stringResource(R.string.budget_issue_retry)) }
                    TextButton(onClick = { onManual(issue) }) { Text(stringResource(R.string.budget_issue_manual)) }
                }

                BudgetIssueReason.STALE_TARGET -> {
                    TextButton(onClick = { onRetry(issue, true) }) { Text(stringResource(R.string.budget_issue_refreeze)) }
                    TextButton(onClick = { onManual(issue) }) { Text(stringResource(R.string.budget_issue_manual)) }
                }
            }
        }
    }
}

@Composable
private fun issueTitle(issue: BudgetIssue, overview: BudgetOverview): String = when (issue.kind) {
    BudgetRecordKind.COPY -> overview.copies[issue.id]?.let { copy ->
        copy.releaseTitle?.takeIf { it.isNotBlank() }?.let { release -> stringResource(R.string.text_pair, copy.vnTitle, release) }
            ?: copy.vnTitle
    }.orEmpty()

    BudgetRecordKind.EXPENSE -> overview.expenses[issue.id]?.let { expense ->
        overview.orderTitles[issue.orderId]?.let { order -> stringResource(R.string.text_pair, order, expense.displayName.localized()) }
            ?: expense.displayName.localized()
    }.orEmpty()
}

@Composable
private fun issueAmount(issue: BudgetIssue, overview: BudgetOverview): String = when (issue.kind) {
    BudgetRecordKind.COPY -> overview.copies[issue.id]?.let { copy ->
        copy.priceMinor?.let { Money.formatWithCode(it, copy.currency) } ?: stringResource(R.string.not_recorded)
    }.orEmpty()

    BudgetRecordKind.EXPENSE -> overview.expenses[issue.id]?.let { Money.formatWithCode(it.amountMinor, it.currency) }.orEmpty()
}

@Composable
private fun outcomeText(outcome: FxFreezeOutcome): String = com.vnventory.app.ui.components.fxOutcomeText(outcome)

private val BudgetIssueReason.labelRes: Int
    get() = when (this) {
        BudgetIssueReason.NO_PRICE -> R.string.budget_issue_no_price
        BudgetIssueReason.NO_DATE -> R.string.budget_issue_no_date
        BudgetIssueReason.NOT_FROZEN -> R.string.budget_issue_not_frozen
        BudgetIssueReason.STALE_TARGET -> R.string.budget_issue_stale
    }

private val BudgetIssueReason.hintRes: Int
    get() = when (this) {
        BudgetIssueReason.NO_PRICE -> R.string.budget_issue_no_price_hint
        BudgetIssueReason.NO_DATE -> R.string.budget_issue_no_date_hint
        BudgetIssueReason.NOT_FROZEN -> R.string.budget_issue_not_frozen_hint
        BudgetIssueReason.STALE_TARGET -> R.string.budget_issue_stale_hint
    }
