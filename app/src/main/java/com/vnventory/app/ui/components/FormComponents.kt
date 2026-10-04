package com.vnventory.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vnventory.app.R
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.ui.text.localized

/** 填写页统一分组，标题与说明只占一个层级，输入控件保留足够宽度。 */
@Composable
fun FormSection(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = if (hint == null) Alignment.CenterVertically else Alignment.Top) {
                ShelfIconTile(icon, size = 32.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            content()
        }
    }
}

/** 窄屏/大字体将币种移到下一行，不挤压金额输入，也不裁掉校验提示。 */
@Composable
fun MoneyInputField(
    value: String,
    currency: String,
    label: String,
    onValueChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 300.dp || scale > 1.2f
        val field: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(value, onValueChange, label = { Text(label) },
                supportingText = supportingText?.let { text -> { Text(text) } }, isError = isError,
                singleLine = true, shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = fieldModifier)
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.currency), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CurrencySelector(currency, onCurrencyChange)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            field(Modifier.weight(1f))
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.currency), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CurrencySelector(currency, onCurrencyChange)
            }
        }
    }
}

@Composable
fun ConditionPicker(
    condition: CopyCondition,
    note: String,
    onConditionChange: (CopyCondition) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CopyCondition.entries.forEach { value ->
                FilterChip(condition == value, { onConditionChange(value) }, label = { Text(value.label.localized()) })
            }
        }
        if (condition == CopyCondition.CUSTOM) OutlinedTextField(
            note, onNoteChange, label = { Text(stringResource(R.string.condition_note)) },
            isError = note.isBlank(), singleLine = true, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
    }
}

@Composable
fun FormSaveBar(
    label: String,
    saving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    error: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error()
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SaveButton(label, saving, enabled, onClick)
            }
        }
    }
}
