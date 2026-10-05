package com.vnventory.app.ui.components

import com.vnventory.app.ui.text.uiTitle
import com.vnventory.app.ui.text.uiReleaseName

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.theme.ShelfMotion
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R
import com.vnventory.app.ui.text.localized

@Composable
fun PageHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, eyebrow: String? = stringResource(R.string.brand_eyebrow)) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        eyebrow?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    val scale = LocalDensity.current.fontScale
    val heading: @Composable () -> Unit = {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || scale > 1.2f) Column {
            heading()
            if (action != null) TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { heading() }
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)),
        shadowElevation = 0.dp,
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
    Button(onClick = onClick, enabled = enabled && !saving, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
        AnimatedContent(saving, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "saveFeedback") { busy ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (busy) stringResource(R.string.saving) else label)
            }
        }
    }
}

/** 收藏卡片：封面优先陈列；书架网格与首页使用 overlayMeta，把日期、价格、手动/品相标签放到封面，正文只留作品名与（可选）版本名。 */
@Composable
fun OwnedCoverCard(copy: OwnedCopy, onClick: () -> Unit, modifier: Modifier = Modifier, sameReleaseCount: Int = 1, showPrice: Boolean = false, ordinal: Int? = null, overlayMeta: Boolean = false, showReleaseName: Boolean = false) {
    PressableSurface(onClick, modifier) {
        Box(Modifier.padding(8.dp)) {
            VnCover(copy.coverUrl, copy.uiTitle(), Modifier.fillMaxWidth().aspectRatio(.70f), corner = 12.dp)
            if (overlayMeta) {
                // 书架网格：手动版本在左上角；品相与盒号合并后放右上角；都不参与封面布局尺寸。
                if (copy.isManualRelease) {
                    CoverOverlayTag(stringResource(R.string.manual_short), Modifier.align(Alignment.TopStart).padding(6.dp).testTag("cover-manual"))
                }
                val condition = copy.condition.label.localized()
                CoverOverlayTag(
                    if (ordinal != null) stringResource(R.string.copy_condition_number, condition, stringResource(R.string.copy_number, ordinal)) else condition,
                    Modifier.align(Alignment.TopEnd).padding(6.dp).testTag("cover-condition-number"),
                )
                if (showPrice) {
                    // 价格固定在右下角；封面放不下与日期并排，日期改为堆叠在价格上方。
                    Column(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        copy.purchaseDate?.let { CoverOverlayTag(it.toString(), Modifier.testTag("cover-purchase-date")) }
                        CoverOverlayTag(copy.priceMinor?.let { Money.formatWithCode(it, copy.currency) } ?: stringResource(R.string.not_recorded), Modifier.testTag("cover-price"))
                    }
                } else {
                    copy.purchaseDate?.let { CoverOverlayTag(it.toString(), Modifier.align(Alignment.BottomStart).padding(6.dp).testTag("cover-purchase-date")) }
                }
            } else if (ordinal != null || sameReleaseCount > 1) {
                Tag(
                    if (ordinal != null) stringResource(R.string.copy_number, ordinal) else stringResource(R.string.copy_multiplier, sameReleaseCount),
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(copy.uiTitle(), style = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading), maxLines = if (overlayMeta) 1 else 2, minLines = if (overlayMeta) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (overlayMeta && showReleaseName) {
                // 默认隐藏；开启后只以弱化样式补充版本名。
                Text(copy.uiReleaseName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (!overlayMeta) {
                Text(copy.uiReleaseName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 书架网格的正文只留作品名与（可选）版本名，状态都在封面上；其他卡片保持原有标签。
            val bodyTags = when {
                overlayMeta -> emptyList()
                copy.isManualRelease -> listOf(copy.condition.label.localized(), stringResource(R.string.manual_short))
                else -> listOf(copy.condition.label.localized())
            }
            if (bodyTags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bodyTags.forEach { Tag(it) }
            }
            if (showPrice && !overlayMeta) Text(copy.priceMinor?.let { Money.formatWithCode(it, copy.currency) } ?: stringResource(R.string.not_recorded), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (!overlayMeta) copy.purchaseDate?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** 封面叠加小标签：深色半透明底配白字，明暗封面上均可读，也不扩大点击区域。 */
@Composable
private fun CoverOverlayTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = .62f),
        contentColor = Color.White,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun OwnedListCard(copy: OwnedCopy, onClick: () -> Unit, modifier: Modifier = Modifier, showPrice: Boolean = false, ordinal: Int? = null, showReleaseName: Boolean = false) {
    PressableSurface(onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            VnCover(copy.coverUrl, copy.uiTitle(), Modifier.width(60.dp).height(86.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(copy.uiTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showReleaseName) Text(copy.uiReleaseName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showPrice) Text(copy.priceMinor?.let { Money.formatWithCode(it, copy.currency) } ?: stringResource(R.string.not_recorded), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag(copy.condition.label.localized())
                    ordinal?.let { Tag(stringResource(R.string.copy_number, it)) }
                    if (copy.isManualRelease) Tag(stringResource(R.string.manual_short))
                    copy.purchaseDate?.let { Tag(it.toString()) }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
