package com.vnventory.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.R
import com.vnventory.app.data.repository.BudgetOverview
import com.vnventory.app.domain.cost.BudgetIssueReason
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.FormSection
import com.vnventory.app.ui.components.OptionGrid
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.di.AppViewModelProvider

@Composable
fun SettingsBudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val backfill by viewModel.backfill.collectAsStateWithLifecycle()
    var editingYear by remember { mutableStateOf<Int?>(null) }

    SettingsDetailScaffold(stringResource(R.string.budget_settings), onBack) { padding ->
        SettingsBudgetContent(
            overview = overview,
            backfill = backfill,
            onEnabled = viewModel::setEnabled,
            onCurrency = viewModel::setCurrency,
            onIncludeFees = viewModel::setIncludeFees,
            onEditYear = { editingYear = it },
            onBackfill = viewModel::backfill,
            modifier = Modifier.padding(padding),
            error = { OperationError(viewModel) },
        )
    }

    editingYear?.let { year ->
        BudgetYearDialog(
            year = year,
            initialAmount = overview.preferences.yearlyAmounts[year],
            currency = overview.targetCurrency,
            onDismiss = { editingYear = null },
            onConfirm = { amount ->
                viewModel.setYearAmount(year, amount)
                editingYear = null
            },
        )
    }
}

@Composable
internal fun SettingsBudgetContent(
    overview: BudgetOverview,
    backfill: BudgetBackfillUiState,
    onEnabled: (Boolean) -> Unit,
    onCurrency: (String) -> Unit,
    onIncludeFees: (Boolean) -> Unit,
    onEditYear: (Int) -> Unit,
    onBackfill: () -> Unit,
    modifier: Modifier = Modifier,
    error: @Composable () -> Unit = {},
) {
    val preferences = overview.preferences
    val years = remember(overview) {
        (preferences.yearlyAmounts.keys + overview.summary.totalsByYear.keys +
            listOf(overview.currentYear - 1, overview.currentYear, overview.currentYear + 1))
            .distinct()
            .sortedDescending()
    }
    val pendingCount = overview.summary.issuesByReason[BudgetIssueReason.NOT_FROZEN].orEmpty().size +
        overview.summary.issuesByReason[BudgetIssueReason.STALE_TARGET].orEmpty().size

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { error() }
        item {
            FormSection(stringResource(R.string.budget_settings), R.drawable.ic_ui_batch) {
                PreferenceSwitchRow(stringResource(R.string.budget_enabled), preferences.enabled, onEnabled,
                    hint = stringResource(R.string.budget_enabled_hint))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.budget_currency), style = MaterialTheme.typography.bodyMedium)
                OptionGrid(
                    Money.commonCurrencies,
                    { code -> stringResource(R.string.currency_option, code, Money.symbol(code).ifEmpty { code }) },
                    overview.targetCurrency,
                    onCurrency,
                    maxColumns = 2,
                )
                Text(stringResource(R.string.budget_currency_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PreferenceSwitchRow(stringResource(R.string.budget_include_fees), preferences.includeFees, onIncludeFees,
                    hint = stringResource(R.string.budget_include_fees_hint))
            }
        }
        item {
            FormSection(stringResource(R.string.budget_yearly_amounts), R.drawable.ic_ui_preferences) {
                Text(stringResource(R.string.budget_yearly_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                years.forEach { year ->
                    val amount = preferences.yearlyAmounts[year]
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onEditYear(year) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.budget_year_label, year), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = amount?.let { Money.format(it, overview.targetCurrency) }
                                ?: stringResource(R.string.budget_year_unset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (amount == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item {
            FormSection(stringResource(R.string.budget_backfill), R.drawable.ic_ui_backup) {
                Text(stringResource(R.string.budget_backfill_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (pendingCount == 0) stringResource(R.string.budget_backfill_done)
                    else stringResource(R.string.budget_backfill_count, pendingCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                backfill.result?.let {
                    Text(
                        stringResource(R.string.budget_backfill_result, it.succeeded, it.pending, it.unsupported, it.failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(onClick = onBackfill, enabled = !backfill.busy && pendingCount > 0, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.budget_backfill_now))
                }
                Spacer(Modifier.fillMaxWidth())
                Text(stringResource(R.string.budget_fx_source_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetYearDialog(
    year: Int,
    initialAmount: Long?,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialAmount?.let { Money.toEditableString(it, currency) }.orEmpty()) }
    val parsed = if (text.isBlank()) null else Money.parse(text, currency)
    val invalid = text.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_year_dialog_title, year)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(currency) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = {
                        Text(
                            if (invalid) stringResource(R.string.amount_invalid)
                            else stringResource(R.string.budget_year_dialog_hint)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed) }, enabled = !invalid) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
