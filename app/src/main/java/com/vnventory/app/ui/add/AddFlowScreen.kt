package com.vnventory.app.ui.add

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.ReleaseInfo
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.ui.components.EmptyState
import com.vnventory.app.ui.components.ErrorState
import com.vnventory.app.ui.components.LoadingState
import com.vnventory.app.ui.components.Tag
import com.vnventory.app.ui.components.VnCover
import com.vnventory.app.ui.components.AddStepIndicator
import com.vnventory.app.ui.components.SaveButton
import com.vnventory.app.ui.components.FormSaveBar
import com.vnventory.app.ui.components.SectionHeading
import com.vnventory.app.ui.components.PressableSurface
import com.vnventory.app.ui.components.PredictiveStepContent
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vnventory.app.R
import com.vnventory.app.ui.text.localized
import com.vnventory.app.ui.text.resolve

@Composable
fun AddFlowScreen(
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    viewModel: AddFlowViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddFlowEvent.Saved -> onSaved(event.copyIds.size)
                is AddFlowEvent.Failed -> snackbarHostState.showSnackbar(resources.resolve(event.message))
            }
        }
    }

    val step = when {
        state.selectedVn == null -> 0
        state.form.releaseId == null && !state.form.manualVersion -> 1
        else -> 2
    }
    val focus = LocalFocusManager.current
    LaunchedEffect(step) { focus.clearFocus() }

    // 退出页一直使用最后的完整数据；返回清空 VN/Release 时不会提前变成空白。
    val stepStates = remember { mutableMapOf<Int, AddFlowUiState>() }
    for (previousStep in 0..step) stepStates[previousStep] = state
    val savedState = rememberSaveableStateHolder()

    fun goBack() {
        when (step) {
            0 -> onBack()
            1 -> viewModel.backToSearch()
            else -> viewModel.backToReleases()
        }
    }

    PredictiveStepContent(step, onBack = { goBack() }, modifier = Modifier.fillMaxSize(), enabled = !state.saving) { displayedStep ->
        val displayedState = if (displayedStep == step) state else stepStates[displayedStep] ?: state
        savedState.SaveableStateProvider(displayedStep) {
            AddStepScaffold(
                step = displayedStep,
                state = displayedState,
                interactive = displayedStep == step && !state.saving,
                onBack = { goBack() },
                onSave = { focus.clearFocus(); viewModel.save() },
                snackbarHostState = snackbarHostState,
            ) {
                when (displayedStep) {
                    0 -> SearchStep(displayedState, viewModel)
                    1 -> ReleasesStep(displayedState, viewModel)
                    else -> PurchaseFormContent(displayedState, viewModel::onPurchaseFormChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStepScaffold(
    step: Int,
    state: AddFlowUiState,
    interactive: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(when (step) {
                            0 -> stringResource(R.string.add_title_search)
                            1 -> stringResource(R.string.add_step_release)
                            else -> stringResource(R.string.add_title_purchase)
                        })
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = interactive) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
                AddStepIndicator(step, Modifier.padding(horizontal = 24.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (step == 2) {
                val order = state.orders.firstOrNull { it.order.id == state.form.orderId }?.order
                FormSaveBar(
                    label = if (state.form.quantity > 1) pluralStringResource(R.plurals.copies_add_label, state.form.quantity, state.form.quantity) else stringResource(R.string.add_save_copy),
                    saving = state.saving, enabled = state.form.canSave && interactive, onClick = onSave,
                    supporting = order?.let { stringResource(R.string.add_order_context, it.title) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            content()
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
            placeholder = { Text(stringResource(R.string.vn_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )

        if (state.search.offline) {
            Text(
                text = stringResource(R.string.vn_offline_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when {
            state.search.loading -> LoadingState(message = stringResource(R.string.vn_searching))

            state.search.error != null && state.search.results.isEmpty() -> ErrorState(
                message = state.search.error.localized(),
                onRetry = viewModel::retrySearch,
            )

            state.search.results.isEmpty() && state.search.hasSearched -> EmptyState(
                title = stringResource(R.string.vn_no_results),
                subtitle = stringResource(R.string.vn_no_results_hint),
            )

            state.search.results.isEmpty() -> EmptyState(
                title = stringResource(R.string.vn_search_title),
                subtitle = stringResource(R.string.vn_search_examples),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.search.results, key = { it.id }) { vn ->
                    VnSearchRow(vn = vn, modifier = Modifier.animateItem(), onClick = { viewModel.selectVn(vn) })
                }
                item {
                    if (state.search.loadingMore) CircularProgressIndicator()
                    else if (state.search.error != null) {
                        Text(state.search.error.localized(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::retrySearch) { Text(stringResource(R.string.vn_retry_page)) }
                    } else if (state.search.hasMore) {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vn_load_more)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VnSearchRow(vn: VnInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressableSurface(onClick, modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        VnCover(
            url = vn.imageUrl,
            contentDescription = vn.displayTitle,
            modifier = Modifier
                .width(48.dp)
                .height(68.dp),
            corner = 8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vn.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            vn.secondaryTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = vn.released?.let { stringResource(R.string.text_pair, vn.id, it) } ?: vn.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Row {
                    VnCover(
                        url = vn.imageUrl,
                        contentDescription = vn.displayTitle,
                        modifier = Modifier
                            .width(72.dp)
                            .height(100.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vn.displayTitle, style = MaterialTheme.typography.titleMedium)
                        vn.secondaryTitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = vn.released?.let { stringResource(R.string.vn_id_release_date, vn.id, it) } ?: vn.id,
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
                onClick = { viewModel.selectManualVersion() },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.release_manual_create),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.release_manual_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        if (releases.loading && releases.releases.isEmpty()) {
            item { LoadingState(modifier = Modifier.heightIn(min = 200.dp), message = stringResource(R.string.release_loading)) }
        }

        releases.error?.let { error ->
            item {
                ErrorState(
                    message = error.localized(),
                    modifier = Modifier.heightIn(min = 200.dp),
                    onRetry = { viewModel.selectVn(vn) },
                )
            }
        }

        if (!releases.loading && releases.error == null && releases.releases.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.release_no_official),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeading(stringResource(R.string.release_options), stringResource(R.string.release_options_hint)) }

        items(releases.releases, key = { it.id }) { release ->
            ReleaseRow(release = release, coverFallback = vn.imageUrl, modifier = Modifier.animateItem(), onClick = {
                viewModel.selectRelease(release)
            })
        }
    }
}

@Composable
private fun ReleaseRow(release: ReleaseInfo, coverFallback: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressableSurface(onClick, modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
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
                text = release.released ?: stringResource(R.string.release_date_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                release.platforms.take(4).forEach { Tag(text = it) }
                if (release.platforms.size > 4) Tag(text = stringResource(R.string.release_more_platforms, release.platforms.size - 4))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (release.languages.isNotEmpty()) {
                        append(release.languages.joinToString("/"))
                    }
                    if (release.publishers.isNotEmpty()) {
                        if (isNotEmpty()) append(stringResource(R.string.separator_dot))
                        append(release.publishers.joinToString(stringResource(R.string.separator_names)))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            release.jan?.let { jan ->
                Text(
                    text = stringResource(R.string.release_barcode, jan),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    }
}
