package com.vnventory.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.theme.ShelfMotion
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vnventory.app.R
import com.vnventory.app.ui.text.localized

@Composable
fun PageHeader(title: String, subtitle: String, modifier: Modifier = Modifier, eyebrow: String = stringResource(R.string.brand_eyebrow)) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** 缩放发生在绘制层，按压时不让整张卡片的内容逐帧重组。 */
@Composable
fun PressableSurface(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .975f else 1f, tween(ShelfMotion.Quick), label = "cardPress")
    Surface(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
        shadowElevation = 1.dp,
    ) { Column(content = content) }
}

@Composable
fun ShelfFab(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.add_collection), expanded: Boolean = true) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        expanded = expanded,
        icon = { Icon(Icons.Default.Add, contentDescription = if (expanded) null else label) },
        text = { Text(label) },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
fun SaveButton(label: String, saving: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled && !saving, modifier = modifier.fillMaxWidth().height(56.dp)) {
        AnimatedContent(saving, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "saveFeedback") { busy ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (busy) stringResource(R.string.saving) else label)
            }
        }
    }
}

/** 同版本仍逐盒展示，编号让价格/品相相同的两盒也能区分。 */
@Composable
fun OwnedCoverCard(copy: OwnedCopy, onClick: () -> Unit, modifier: Modifier = Modifier, sameReleaseCount: Int = 1) {
    PressableSurface(onClick, modifier) {
        Box(Modifier.padding(6.dp)) {
            VnCover(copy.coverUrl, copy.vnTitle, Modifier.fillMaxWidth().aspectRatio(.70f), corner = 16.dp)
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(copy.condition.label.localized(), emphasized = true)
                if (copy.isManualRelease) Tag(stringResource(R.string.manual_short))
            }
            if (sameReleaseCount > 1) Tag(pluralStringResource(R.plurals.same_release_count, sameReleaseCount, sameReleaseCount), Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.vnTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(copy.displayReleaseName.localized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Money.formatWithCode(copy.priceMinor, copy.currency), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val number = stringResource(R.string.copy_number, copy.id)
            Text(copy.purchaseDate?.let { stringResource(R.string.text_pair, number, it) } ?: number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun OwnedListCard(copy: OwnedCopy, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableSurface(onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VnCover(copy.coverUrl, copy.vnTitle, Modifier.width(64.dp).height(92.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(copy.vnTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(copy.displayReleaseName.localized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.formatWithCode(copy.priceMinor, copy.currency), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(copy.condition.label.localized())
                    Text(stringResource(R.string.copy_number, copy.id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun CostHighlight(totals: Map<String, Long>, label: String, modifier: Modifier = Modifier, supporting: String? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.primaryContainer) {
        Column(
            Modifier.animateContentSize(tween(ShelfMotion.Standard))
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            if (totals.isEmpty()) Text(stringResource(R.string.empty_value), style = MaterialTheme.typography.headlineMedium, color = colors.onPrimaryContainer)
            totals.entries.sortedBy { it.key }.forEach { (currency, amount) ->
                // 精确金额直接展示；只动画容器高度，不让数字出现虚假中间值。
                Text(Money.formatWithCode(amount, currency), style = MaterialTheme.typography.headlineMedium, color = colors.onPrimaryContainer)
            }
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer.copy(alpha = .8f)) }
        }
    }
}
