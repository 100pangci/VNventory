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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.selectableGroup
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
                 modifier = fieldModifier.testTag("money-input"))
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.currency), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CurrencySelector(currency, onCurrencyChange)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            field(Modifier.weight(1f))
            CurrencySelector(currency, onCurrencyChange, Modifier.width(104.dp).testTag("money-currency"), label = stringResource(R.string.currency))
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
        OptionGrid(CopyCondition.entries, { it.label.localized() }, condition, onConditionChange)
        if (condition == CopyCondition.CUSTOM) OutlinedTextField(
            note, onNoteChange, label = { Text(stringResource(R.string.condition_note)) },
            isError = note.isBlank(), singleLine = true, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
    }
}

/** 等宽、等高的单选项；大字体减少列数，保留完整文字与最小触控高度。 */
@Composable
fun <T> OptionGrid(
    options: List<T>, label: @Composable (T) -> String, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, maxColumns: Int = 3, enabled: (T) -> Boolean = { true },
) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        val columns = ((maxWidth.value + 8f) / (88f * scale + 8f)).toInt().coerceIn(1, maxColumns)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        val active = option == selected
                        Surface(selected = active, onClick = { onSelect(option) }, enabled = enabled(option),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).fillMaxHeight(),
                            shape = MaterialTheme.shapes.small,
                             color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (!enabled(option)) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                                 else if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(label(option), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
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
