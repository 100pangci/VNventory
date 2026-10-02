package com.vnventory.app.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.ui.components.CurrencySelector
import com.vnventory.app.ui.components.DateField
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.ErrorState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.LabeledRow
import com.vnventory.app.ui.components.OrderSelector
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFlowScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    orderContextId: Long?,
    viewModel: AddFlowViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddFlowEvent.Saved -> onSaved()
                is AddFlowEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val step = when {
        state.selectedVn == null -> 0
        state.form.releaseId == null && !state.form.manualVersion -> 1
        else -> 2
    }

    fun goBack() {
        when (step) {
            0 -> onBack()
            1 -> viewModel.backToSearch()
            else -> viewModel.backToReleases()
        }
    }

    BackHandler(enabled = step > 0) { goBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            0 -> "搜索 VN"
                            1 -> "选择版本"
                            else -> "购入信息"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (step == 2) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (orderContextId != null) {
                            Text(
                                text = "将加入所选订单（可修改）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.save() },
                            enabled = state.form.canSave && !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).width(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.form.quantity > 1) "保存 ${state.form.quantity} 盒" else "保存收藏")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (step) {
                0 -> SearchStep(state, viewModel)
                1 -> ReleasesStep(state, viewModel)
                else -> FormStep(state, viewModel)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 步骤 1：搜索
// ---------------------------------------------------------------------------

@Composable
private fun SearchStep(state: AddFlowUiState, viewModel: AddFlowViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.search.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("输入游戏名（罗马字 / 日文原名 / 中文译名）") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (state.search.offline) {
            Text(
                text = "网络不可用：以下为本地缓存结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when {
            state.search.loading -> LoadingState(message = "搜索中…")

            state.search.error != null -> ErrorState(
                message = state.search.error,
                onRetry = { viewModel.onQueryChange(state.search.query + " ") },
            )

            state.search.results.isEmpty() && state.search.hasSearched -> EmptyState(
                title = "没有找到结果",
                subtitle = "试试官方标题或日文原名；也可以先随便选一个 VN 再创建手动版本",
            )

            state.search.results.isEmpty() -> EmptyState(
                title = "从 VNDB 搜索作品",
                subtitle = "例如：Ever17、月に寄りそう乙女の作法、サクラノ詩",
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.search.results, key = { it.id }) { vn ->
                    VnSearchRow(vn = vn, onClick = { viewModel.selectVn(vn) })
                }
            }
        }
    }
}

@Composable
private fun VnSearchRow(vn: VnInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        VnCover(
            url = vn.imageUrl,
            contentDescription = vn.title,
            modifier = Modifier
                .width(48.dp)
                .height(68.dp),
            corner = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vn.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            vn.altTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = buildString {
                    append(vn.id)
                    vn.released?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 步骤 2：选择 Release / 手动版本
// ---------------------------------------------------------------------------

@Composable
private fun ReleasesStep(state: AddFlowUiState, viewModel: AddFlowViewModel) {
    val vn = state.selectedVn ?: return
    val releases = state.releases

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Row {
                    VnCover(
                        url = vn.imageUrl,
                        contentDescription = vn.title,
                        modifier = Modifier
                            .width(72.dp)
                            .height(100.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vn.title, style = MaterialTheme.typography.titleMedium)
                        vn.altTitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = buildString {
                                append(vn.id)
                                vn.released?.let { append(" · 发售 ").append(it) }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                vn.description?.let { desc ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = desc.replace(Regex("\\[/?url[^\\]]*\\]"), "").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectManualVersion() },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "找不到对应版本？创建手动版本",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "手动版本先记录一盒，之后可以在详情里绑定到 VNDB Release",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        if (releases.loading && releases.releases.isEmpty()) {
            item { LoadingState(modifier = Modifier.height(200.dp), message = "加载版本列表…") }
        }

        releases.error?.let { error ->
            item {
                ErrorState(
                    message = error,
                    modifier = Modifier.height(200.dp),
                    onRetry = { viewModel.selectVn(vn) },
                )
            }
        }

        if (!releases.loading && releases.error == null && releases.releases.isEmpty()) {
            item {
                Text(
                    text = "VNDB 上没有该作品的版本记录，请使用手动版本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(releases.releases, key = { it.id }) { release ->
            ReleaseRow(release = release, coverFallback = vn.imageUrl, onClick = {
                viewModel.selectRelease(release)
            })
        }
    }
}

@Composable
private fun ReleaseRow(release: ReleaseInfo, coverFallback: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        VnCover(
            url = release.displayImage() ?: coverFallback,
            contentDescription = release.title,
            modifier = Modifier
                .width(64.dp)
                .height(88.dp),
            corner = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(release.released ?: "发售日未知")
                    if (release.official == false) append(" · 非官方")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                release.platforms.take(4).forEach { Tag(text = it) }
                if (release.platforms.size > 4) Tag(text = "+${release.platforms.size - 4}")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (release.languages.isNotEmpty()) {
                        append(release.languages.joinToString("/"))
                    }
                    if (release.publishers.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(release.publishers.joinToString("、"))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            release.jan?.let { jan ->
                Text(
                    text = "JAN/EAN: $jan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 步骤 3：购入信息
// ---------------------------------------------------------------------------

@Composable
private fun FormStep(state: AddFlowUiState, viewModel: AddFlowViewModel) {
    val vn = state.selectedVn ?: return
    val form = state.form
    val release = form.releaseId?.let { id -> state.releases.releases.firstOrNull { it.id == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 选中对象
        Row {
            VnCover(
                url = release?.displayImage() ?: vn.imageUrl,
                contentDescription = vn.title,
                modifier = Modifier
                    .width(64.dp)
                    .height(88.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(vn.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = release?.title ?: "手动版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = release?.id ?: vn.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (form.manualVersion) {
            OutlinedTextField(
                value = form.releaseTitle,
                onValueChange = viewModel::onManualTitleChange,
                label = { Text("手动版本名称（如：初回限定版 / 某店特典）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 价格 + 币种
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = form.priceText,
                onValueChange = viewModel::onPriceChange,
                label = { Text("购入价格") },
                supportingText = {
                    val parsed = form.parsedPrice
                    Text(
                        when {
                            form.priceText.isBlank() -> "留空按 0 计算"
                            parsed != null -> "= ${com.vnventory.app.domain.model.Money.format(parsed, form.currency)}"
                            else -> "金额格式不正确"
                        }
                    )
                },
                isError = !form.priceValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            CurrencySelector(
                selected = form.currency,
                onSelect = viewModel::onCurrencyChange,
            )
        }

        // 品相
        Column {
            Text("品相", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CopyCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = form.condition == condition,
                        onClick = { viewModel.onConditionChange(condition) },
                        label = { Text(condition.label) },
                    )
                }
            }
        }

        if (form.condition == CopyCondition.CUSTOM) {
            OutlinedTextField(
                value = form.conditionNote,
                onValueChange = viewModel::onConditionNoteChange,
                label = { Text("自定义品相说明") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 日期 / 店铺 / 数量 / 订单
        LabeledRow("购买日期") {
            DateField(date = form.purchaseDate, onDateChange = viewModel::onPurchaseDateChange)
        }

        OutlinedTextField(
            value = form.shop,
            onValueChange = viewModel::onShopChange,
            label = { Text("店铺 / 渠道（可空）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LabeledRow("数量（盒）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.onQuantityChange(form.quantity - 1) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = form.quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { viewModel.onQuantityChange(form.quantity + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "增加")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "同版本多盒会分别保存，价格可稍后单独修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LabeledRow("所属订单") {
            OrderSelector(
                orders = state.orders,
                selectedId = form.orderId,
                onSelect = viewModel::onOrderChange,
            )
        }

        OutlinedTextField(
            value = form.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text("备注（可空）") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
    }
}
