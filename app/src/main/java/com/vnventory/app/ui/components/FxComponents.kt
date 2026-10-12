package com.vnventory.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vnventory.app.R
import com.vnventory.app.data.repository.FxFreezeOutcome
import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.model.Money
import java.time.LocalDate

/** 一条记录当前的定格快照（展示用）。 */
data class FxSnapshotInfo(
    val rateScaled: Long,
    val date: LocalDate,
    val toCurrency: String,
    val source: String,
)

/** 定格来源展示：手动使用本地化文案，数据源直接显示名称。 */
@Composable
fun fxSourceLabel(source: String): String =
    if (source == Fx.SOURCE_MANUAL) stringResource(R.string.fx_source_manual) else source

/** 编辑表单中的汇率定格行：状态 + 重新获取 / 手动填写 / 清除。 */
@Composable
fun FxStatusContent(
    fromCurrency: String,
    targetCurrency: String,
    snapshot: FxSnapshotInfo?,
    onRetry: (() -> Unit)?,
    onManual: (() -> Unit)?,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.fx_snapshot_title), style = MaterialTheme.typography.labelMedium)
        val stale = snapshot != null && Money.normalize(snapshot.toCurrency) != Money.normalize(targetCurrency)
        val status = when {
            snapshot == null -> stringResource(R.string.fx_status_missing)
            stale -> stringResource(R.string.fx_status_stale, Money.normalize(snapshot.toCurrency))
            else -> stringResource(
                R.string.fx_status_frozen,
                Fx.format(snapshot.rateScaled, fromCurrency, targetCurrency),
                snapshot.date.toString(),
                fxSourceLabel(snapshot.source),
            )
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowChips {
            onRetry?.let { action -> TextButton(onClick = action) { Text(stringResource(R.string.fx_retry)) } }
            onManual?.let { action -> TextButton(onClick = action) { Text(stringResource(R.string.fx_manual)) } }
            onClear?.let { action -> TextButton(onClick = action) { Text(stringResource(R.string.fx_clear)) } }
        }
    }
}

/** 手动填写汇率对话框：1 from = ? to；日期必填。 */
@Composable
fun FxManualDialog(
    fromCurrency: String,
    toCurrency: String,
    initialRateText: String,
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
    onConfirm: (rateScaled: Long, date: LocalDate) -> Unit,
) {
    var rateText by rememberSaveable { mutableStateOf(initialRateText) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    val parsed = Fx.parse(rateText)
    val invalid = rateText.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fx_manual_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text(stringResource(R.string.fx_manual_rate_label, Money.normalize(fromCurrency), Money.normalize(toCurrency))) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.fx_manual_rate_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.fx_manual_date), style = MaterialTheme.typography.labelMedium)
                DateField(date, { date = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { rate -> date?.let { day -> onConfirm(rate, day) } } },
                enabled = parsed != null && date != null,
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            RowChips {
                onClear?.let { action -> TextButton(onClick = action) { Text(stringResource(R.string.fx_clear)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** 定格结果反馈文案（预算页与编辑表单共用）。 */
@Composable
fun fxOutcomeText(outcome: FxFreezeOutcome): String = stringResource(
    when (outcome) {
        FxFreezeOutcome.FROZEN -> R.string.fx_outcome_frozen
        FxFreezeOutcome.PENDING -> R.string.fx_outcome_pending
        FxFreezeOutcome.UNSUPPORTED -> R.string.fx_outcome_unsupported
        FxFreezeOutcome.FAILED -> R.string.fx_outcome_failed
        FxFreezeOutcome.SKIPPED -> R.string.fx_outcome_skipped
    }
)

@Composable
private fun RowChips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}
