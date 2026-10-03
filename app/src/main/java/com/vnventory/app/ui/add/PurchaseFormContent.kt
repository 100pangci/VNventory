package com.vnventory.app.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.CurrencySelector
import com.vnventory.app.ui.components.DateField
import com.vnventory.app.ui.components.OrderSelector
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.VnCover

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

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PurchaseSection("所选版本") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                VnCover(release?.displayImage() ?: vn.imageUrl, vn.displayTitle, Modifier.width(56.dp).height(80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(vn.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        release?.title ?: "手动版本",
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
                    label = { Text("版本名称") },
                    supportingText = { Text("例如：初回限定版、某店特典") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        PurchaseSection("价格与数量", "填写单盒价格，不包含运费、手续费等批次费用。") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = form.priceText,
                    onValueChange = { onFormChange(form.copy(priceText = it)) },
                    label = { Text("单盒价格") },
                    supportingText = { Text(if (!form.priceValid) "金额格式不正确" else "留空按 0 计算") },
                    isError = !form.priceValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("币种", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CurrencySelector(form.currency, { onFormChange(form.copy(currency = it)) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("数量（盒）", style = MaterialTheme.typography.bodyMedium)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onFormChange(form.copy(quantity = form.quantity - 1)) },
                            enabled = form.quantity > 1,
                            modifier = Modifier.semantics { contentDescription = "减少数量" },
                        ) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(form.quantity.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { onFormChange(form.copy(quantity = form.quantity + 1)) }, enabled = form.quantity < 99) {
                            Icon(Icons.Filled.Add, contentDescription = "增加数量")
                        }
                    }
                }
            }
            Text("多盒会分别保存，之后可逐盒修改价格与品相。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("商品小计 · 不含批次费用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val subtotal = form.subtotalMinor
                Text(
                    text = when {
                        subtotal != null -> Money.formatWithCode(subtotal, form.currency)
                        !form.priceValid -> "请检查单盒价格"
                        else -> "商品小计超出可支持的范围"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (subtotal == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        PurchaseSection("品相", "本次添加统一填写，保存后仍可逐盒调整。") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CopyCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = form.condition == condition,
                        onClick = { onFormChange(form.copy(condition = condition)) },
                        label = { Text(condition.label) },
                    )
                }
            }
            AnimatedVisibility(form.condition == CopyCondition.CUSTOM) {
                OutlinedTextField(
                    value = form.conditionNote,
                    onValueChange = { onFormChange(form.copy(conditionNote = it)) },
                    label = { Text("自定义品相说明") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        PurchaseSection("购买记录", "选填 · 用于日后回溯这次购入。") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("购买日期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DateField(form.purchaseDate, { onFormChange(form.copy(purchaseDate = it)) }, modifier = Modifier.fillMaxWidth(), placeholder = "选择购买日期（选填）")
                }
                OutlinedTextField(
                    value = form.shop,
                    onValueChange = { onFormChange(form.copy(shop = it)) },
                    label = { Text("店铺 / 渠道") },
                    placeholder = { Text("例如：駿河屋、メルカリ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("所属购买批次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OrderSelector(state.orders, form.orderId, { onFormChange(form.copy(orderId = it)) })
                    Text(
                        if (form.orderId == null) "不加入批次时，这盒作为独立收藏保存。" else "运费、手续费等请在该批次中记录并分摊。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        PurchaseSection("备注", "选填 · 特典、缺件或其他想记下的细节。") {
            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                placeholder = { Text("写下一点关于这盒的记录…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PurchaseSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}
