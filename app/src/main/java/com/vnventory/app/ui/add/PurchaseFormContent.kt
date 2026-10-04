package com.vnventory.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.DateField
import com.vnventory.app.ui.components.OrderSelector
import com.vnventory.app.ui.components.FormSection
import com.vnventory.app.ui.components.MoneyInputField
import com.vnventory.app.ui.components.ConditionPicker
import com.vnventory.app.ui.components.VnCover
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R
import com.vnventory.app.ui.components.ShopChannelField

/** 按填写目的分区，不把必填、选填和解释文字挤在同一层级。 */
@Composable
internal fun PurchaseFormContent(
    state: AddFlowUiState,
    onFormChange: (PurchaseFormState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vn = state.selectedVn ?: return
    val form = state.form
    val release = state.releases.releases.firstOrNull { it.id == form.releaseId }
    val decreaseDescription = stringResource(R.string.quantity_decrease)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FormSection(stringResource(R.string.purchase_selected_release), R.drawable.ic_ui_shelf) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                VnCover(release?.displayImage() ?: vn.imageUrl, vn.displayTitle, Modifier.width(56.dp).height(80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(vn.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        release?.title ?: stringResource(R.string.message_manual_release),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(release?.id ?: vn.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (form.manualVersion) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = form.releaseTitle,
                    onValueChange = { onFormChange(form.copy(releaseTitle = it)) },
                    label = { Text(stringResource(R.string.release_name)) },
                    supportingText = { Text(stringResource(R.string.release_name_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FormSection(stringResource(R.string.purchase_price_quantity), R.drawable.ic_ui_batch, hint = stringResource(R.string.purchase_price_quantity_hint)) {
            MoneyInputField(form.priceText, form.currency, stringResource(R.string.purchase_unit_price),
                { onFormChange(form.copy(priceText = it)) }, { onFormChange(form.copy(currency = it)) },
                isError = !form.priceValid,
                supportingText = stringResource(if (!form.priceValid) R.string.amount_invalid else R.string.amount_blank_unknown))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.purchase_quantity), style = MaterialTheme.typography.bodyMedium)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onFormChange(form.copy(quantity = form.quantity - 1)) },
                            enabled = form.quantity > 1,
                            modifier = Modifier.semantics { contentDescription = decreaseDescription },
                        ) {
                            Text(stringResource(R.string.quantity_minus), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(form.quantity.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onFormChange(form.copy(quantity = form.quantity + 1)) }, enabled = form.quantity < 99) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quantity_increase))
                        }
                    }
                }
            }
            Text(stringResource(R.string.purchase_multiple_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.purchase_subtotal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val subtotal = form.subtotalMinor
                Text(
                    text = when {
                        form.priceText.isBlank() -> stringResource(R.string.not_recorded)
                        subtotal != null -> Money.formatWithCode(subtotal, form.currency)
                        !form.priceValid -> stringResource(R.string.purchase_price_check)
                        else -> stringResource(R.string.purchase_subtotal_overflow)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (subtotal == null && form.priceText.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        FormSection(stringResource(R.string.condition), R.drawable.ic_ui_preferences, hint = stringResource(R.string.purchase_condition_hint)) {
            ConditionPicker(form.condition, form.conditionNote,
                { onFormChange(form.copy(condition = it)) }, { onFormChange(form.copy(conditionNote = it)) })
        }

        FormSection(stringResource(R.string.purchase_records), R.drawable.ic_ui_shop, hint = stringResource(R.string.purchase_records_hint)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.purchase_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DateField(form.purchaseDate, { onFormChange(form.copy(purchaseDate = it)) }, modifier = Modifier.fillMaxWidth(), placeholder = stringResource(R.string.purchase_date_optional))
                }
                ShopChannelField(
                    value = form.shop,
                    onValueChange = { onFormChange(form.copy(shop = it)) },
                    options = state.shopChannels,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.purchase_order), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OrderSelector(state.orders, form.orderId, { onFormChange(form.copy(orderId = it)) }, Modifier.fillMaxWidth())
                    Text(
                        stringResource(if (form.orderId == null) R.string.purchase_standalone_hint else R.string.purchase_order_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FormSection(stringResource(R.string.notes), R.drawable.ic_ui_info, hint = stringResource(R.string.purchase_notes_hint)) {
            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                placeholder = { Text(stringResource(R.string.purchase_notes_placeholder)) },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
