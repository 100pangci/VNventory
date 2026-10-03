package com.vnventory.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OrderSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.vnventory.app.ui.theme.ShelfMotion

// ---------------------------------------------------------------------------
// 封面
// ---------------------------------------------------------------------------

/** 统一封面：加载失败/为空时显示占位。列表里固定宽高比由调用方给出。 */
@Composable
fun VnCover(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val shape = RoundedCornerShape(corner)
    val context = LocalContext.current
    val localDrawableId = remember(url, context.packageName) {
        url?.let(Uri::parse)
            ?.takeIf { it.scheme == "android.resource" && it.authority == context.packageName }
            ?.lastPathSegment?.toIntOrNull()
    }
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    var imageStatus by remember(url) { mutableStateOf(if (url.isNullOrBlank()) "暂无封面" else "封面加载中") }
    val image = remember(context, url, motionScale) {
        ImageRequest.Builder(context).data(url)
            .crossfade((ShelfMotion.Standard * motionScale).toInt().coerceAtLeast(0))
            .build()
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .semantics { stateDescription = if (localDrawableId != null) "封面已加载" else imageStatus },
        contentAlignment = Alignment.Center,
    ) {
        // 占位始终在图像后方；空 URL、加载中和网络失败均不会变成空白矩形。
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BrandMark(Modifier.size(38.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .6f))
            Text("VN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .6f))
        }
        if (localDrawableId != null) {
            Image(
                painter = painterResource(localDrawableId),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!url.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onLoading = { imageStatus = "封面加载中" },
                onSuccess = { imageStatus = "封面已加载" },
                onError = { imageStatus = "封面暂不可用" },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 状态视图
// ---------------------------------------------------------------------------

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            BrandMark(Modifier.size(36.dp))
            CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 2.dp)
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
            Text("!", modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "出错了",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))),
            contentAlignment = Alignment.Center,
        ) { BrandMark(Modifier.size(48.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------------------
// 小标签
// ---------------------------------------------------------------------------

@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 金额
// ---------------------------------------------------------------------------

@Composable
fun MoneyAmountText(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(text = Money.formatWithCode(minor, currency), style = style, color = color, modifier = modifier)
}

/** 多币种合计，如 “¥1,200 + $30”；无数据显示 [emptyText] */
@Composable
fun MoneyTotalsInline(
    totals: Map<String, Long>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emptyText: String = "—",
) {
    val text = if (totals.isEmpty()) {
        emptyText
    } else {
        totals.entries.sortedBy { it.key }.joinToString(" + ") { (currency, amount) ->
            Money.formatWithCode(amount, currency)
        }
    }
    Text(text = text, style = style, color = color, modifier = modifier)
}

// ---------------------------------------------------------------------------
// 表单辅助
// ---------------------------------------------------------------------------

/** 详情页“标签 + 值”行 */
@Composable
fun LabeledRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)),
    ) {
        Column(modifier = Modifier.animateContentSize(tween(ShelfMotion.Standard)).padding(20.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

/** 货币选择（下拉） */
@Composable
fun CurrencySelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    options: List<String> = Money.commonCurrencies,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text("$code（${Money.symbol(code).ifEmpty { code }}）") },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}

/** 订单选择（下拉，可“不加入订单”） */
@Composable
fun OrderSelector(
    orders: List<OrderSummary>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = orders.firstOrNull { it.order.id == selectedId }?.order?.title ?: "不加入订单"
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("不加入订单") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            orders.forEach { summary ->
                DropdownMenuItem(
                    text = { Text("${summary.order.title}（${summary.order.currency}）") },
                    onClick = {
                        expanded = false
                        onSelect(summary.order.id)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 日期
// ---------------------------------------------------------------------------

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** 日期选择对话框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toUtcMillis(),
        initialDisplayedMonthMillis = initial?.toUtcMillis(),
        initialDisplayMode = DisplayMode.Picker,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.selectedDateMillis?.toLocalDateUtc())
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        // 与 DateNote-Weii 1a5d6f4 一致：只重建模式 UI，保留选择状态，
        // 避免 Material 3 日历/输入切换时运行卡顿的 AnimatedContent 转场。
        key(state.displayMode) {
            DatePicker(state = state, showModeToggle = true)
        }
    }
}

/** 日期行：显示当前值，点击弹日期选择 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "选择日期",
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Text(date?.toString() ?: placeholder)
    }
    if (showPicker) {
        DatePickerModal(
            initial = date,
            onDismiss = { showPicker = false },
            onConfirm = onDateChange,
        )
    }
}
