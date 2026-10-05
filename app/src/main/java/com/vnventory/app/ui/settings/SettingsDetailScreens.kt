package com.vnventory.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vnventory.app.ui.components.OptionGrid
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.BuildConfig
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.AppearancePreferences
import com.vnventory.app.domain.model.ThemeMode
import com.vnventory.app.domain.model.TitleDisplayMode
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.AppLogo
import com.vnventory.app.ui.components.SectionCard
import com.vnventory.app.ui.components.FormSection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vnventory.app.R
import com.vnventory.app.domain.text.Message
import com.vnventory.app.ui.text.localized

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    busy: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    BackHandler(enabled = busy) { /* 完成操作后再返回，避免中途取消恢复。 */ }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
fun SettingsPreferencesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val currency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val shelfPrices by viewModel.showShelfPrices.collectAsStateWithLifecycle()
    val priceStats by viewModel.showPriceStats.collectAsStateWithLifecycle()
    val shelfReleaseNames by viewModel.showShelfReleaseNames.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val titleDisplayMode by viewModel.titleDisplayMode.collectAsStateWithLifecycle()
    SettingsDetailScaffold(stringResource(R.string.settings_preferences), onBack) { padding ->
        SettingsPreferencesContent(currency, appearance, shelfPrices, priceStats, shelfReleaseNames,
            viewModel::setThemeMode, viewModel::setDynamicColor, viewModel::setShowShelfPrices, viewModel::setShowPriceStats, viewModel::setShowShelfReleaseNames,
            viewModel::setDefaultCurrency, Modifier.padding(padding), error = { OperationError(viewModel) },
            titleDisplayMode = titleDisplayMode, onTitleDisplayMode = viewModel::setTitleDisplayMode)
    }
}

@Composable
internal fun SettingsPreferencesContent(
    currency: String, appearance: AppearancePreferences, shelfPrices: Boolean, priceStats: Boolean, shelfReleaseNames: Boolean,
    onThemeMode: (ThemeMode) -> Unit, onDynamicColor: (Boolean) -> Unit,
    onShelfPrices: (Boolean) -> Unit, onPriceStats: (Boolean) -> Unit, onShelfReleaseNames: (Boolean) -> Unit, onCurrency: (String) -> Unit,
    modifier: Modifier = Modifier, error: @Composable () -> Unit = {},
    titleDisplayMode: TitleDisplayMode = TitleDisplayMode.ORIGINAL,
    onTitleDisplayMode: (TitleDisplayMode) -> Unit = {},
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { error() }
        item {
            FormSection(stringResource(R.string.settings_appearance), R.drawable.ic_ui_preferences) {
                AppearancePreferencesContent(appearance, onThemeMode, onDynamicColor)
            }
        }
        item {
            FormSection(stringResource(R.string.price_display_settings), R.drawable.ic_ui_shelf) {
                PriceDisplayPreferences(shelfPrices, priceStats, onShelfPrices, onPriceStats)
            }
        }
        item {
            FormSection(stringResource(R.string.settings_shelf_display), R.drawable.ic_ui_grid) {
                Text(stringResource(R.string.title_display_mode), style = MaterialTheme.typography.bodyMedium)
                OptionGrid(TitleDisplayMode.entries,
                    { stringResource(if (it == TitleDisplayMode.ORIGINAL) R.string.title_original else R.string.title_romanized) },
                    titleDisplayMode, onTitleDisplayMode, maxColumns = 2)
                PreferenceSwitchRow(stringResource(R.string.show_shelf_release_names), shelfReleaseNames, onShelfReleaseNames,
                    hint = stringResource(R.string.show_shelf_release_names_hint))
            }
        }
        item {
            FormSection(stringResource(R.string.settings_default_currency), R.drawable.ic_ui_batch, hint = stringResource(R.string.settings_default_currency_hint)) {
                OptionGrid(Money.commonCurrencies,
                    { code -> stringResource(R.string.currency_option, code, Money.symbol(code).ifEmpty { code }) },
                    currency, onCurrency, maxColumns = 2)
                if (currency !in Money.commonCurrencies) InfoLine(stringResource(R.string.settings_currency_current, currency))
            }
        }
    }
}

@Composable
internal fun AppearancePreferencesContent(
    appearance: AppearancePreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dynamicColorSupported: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = appearance.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp).fillMaxHeight(),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(mode.label.localized(), style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading), textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PreferenceSwitchRow(stringResource(R.string.settings_dynamic_color), appearance.dynamicColor, onDynamicColorChange,
            Modifier.testTag("dynamic-color-row"), enabled = dynamicColorSupported,
            hint = stringResource(if (dynamicColorSupported) R.string.settings_dynamic_color_hint else R.string.settings_dynamic_color_unavailable))
    }
}

@Composable
internal fun PriceDisplayPreferences(shelfPrices: Boolean, priceStats: Boolean, onShelfPrices: (Boolean) -> Unit, onPriceStats: (Boolean) -> Unit) {
    Column {
        PreferenceSwitchRow(stringResource(R.string.show_shelf_prices), shelfPrices, onShelfPrices)
        PreferenceSwitchRow(stringResource(R.string.show_price_stats), priceStats, onPriceStats)
    }
}

/** 整行只保留一个开关动作，按住时的反馈与圆角触控范围一致。 */
@Composable
private fun PreferenceSwitchRow(
    title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, hint: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
        .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = .08f) else Color.Transparent)
        .toggleable(checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SettingsDataScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.backupState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readBackup)
    }
    SettingsDetailScaffold(stringResource(R.string.settings_backup), onBack, state.busy) { padding ->
        SettingsDataContent(
            state = state,
            modifier = Modifier.padding(padding),
            onExport = { exportLauncher.launch("VNventory-backup-${LocalDate.now()}.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            error = { OperationError(viewModel) },
        )
    }
    BackupImportDialogs(
        state, viewModel::setRestoreCurrency, viewModel::dismissImport,
        { viewModel.restoreBackup(false) }, viewModel::requestReplace,
        viewModel::cancelReplace, { viewModel.restoreBackup(true) },
        error = actionError,
        onRestoreShops = viewModel::setRestoreShops,
    )
}

@Composable
internal fun SettingsDataContent(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    error: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { error() }
        if (state.busy) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress.localized(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.feedback?.let { feedback -> item { Text(feedback.localized(), color = MaterialTheme.colorScheme.primary) } }
        item {
            FormSection(stringResource(R.string.backup_export), R.drawable.ic_ui_backup) {
                InfoLine(stringResource(R.string.backup_export_hint))
                InfoLine(stringResource(R.string.backup_snapshot_hint))
                Button(onClick = onExport, enabled = !state.busy && state.pendingImport == null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_export))
                }
            }
        }
        item {
            FormSection(stringResource(R.string.backup_restore), R.drawable.ic_ui_backup) {
                InfoLine(stringResource(R.string.backup_restore_hint))
                InfoLine(stringResource(R.string.backup_replace_hint))
                OutlinedButton(onClick = onImport, enabled = !state.busy && state.pendingImport == null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_select_file))
                }
            }
        }
        item {
            FormSection(stringResource(R.string.backup_notice), R.drawable.ic_ui_info) {
                InfoLine(stringResource(R.string.backup_private_hint))
                InfoLine(stringResource(R.string.backup_uninstall_hint))
                InfoLine(stringResource(R.string.backup_file_access_hint))
            }
        }
    }
}

@Composable
internal fun BackupImportDialogs(
    state: BackupUiState,
    onRestoreCurrency: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onAppend: () -> Unit,
    onRequestReplace: () -> Unit,
    onCancelReplace: () -> Unit,
    onReplace: () -> Unit,
    error: Message? = null,
    onRestoreShops: (Boolean) -> Unit = {},
) {
    val backup = state.pendingImport ?: return
    if (!state.replaceConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onDismiss() },
            title = { Text(stringResource(R.string.backup_check)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_contents,
                        pluralStringResource(R.plurals.backup_copy_count, backup.copies.size, backup.copies.size),
                        pluralStringResource(R.plurals.backup_order_count, backup.orders.size, backup.orders.size),
                        pluralStringResource(R.plurals.backup_expense_count, backup.expenses.size, backup.expenses.size)))
                    val exported = runCatching {
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(backup.exportedAt).atZone(ZoneId.systemDefault()))
                    }.getOrDefault(stringResource(R.string.unknown))
                    InfoLine(stringResource(R.string.backup_exported_at, exported))
                    InfoLine(stringResource(R.string.backup_import_modes_hint))
                    InfoLine(stringResource(R.string.backup_price_display_hint))
                    if (backup.appearance != null) InfoLine(stringResource(R.string.backup_appearance_hint))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.restoreCurrency, onCheckedChange = onRestoreCurrency, enabled = !state.busy)
                        Text(stringResource(R.string.backup_restore_currency, backup.defaultCurrency), style = MaterialTheme.typography.bodyMedium)
                    }
                    backup.shopChannels?.let { shops ->
                        InfoLine(pluralStringResource(R.plurals.shops_count, shops.size, shops.size))
                        InfoLine(stringResource(R.string.backup_restore_shops_hint))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.restoreShops, onCheckedChange = onRestoreShops, enabled = !state.busy)
                            Text(stringResource(R.string.backup_restore_shops), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.busy) Text(state.progress.localized())
                    error?.let { Text(it.localized(), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onAppend, enabled = !state.busy) { Text(stringResource(R.string.backup_append)) }
                    TextButton(onClick = onRequestReplace, enabled = !state.busy) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onCancelReplace() },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_replace_warning))
                    if (state.busy) Text(state.progress.localized())
                    error?.let { Text(it.localized(), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = onCancelReplace, enabled = !state.busy) { Text(stringResource(R.string.action_back)) } },
            confirmButton = { TextButton(onClick = onReplace, enabled = !state.busy) { Text(stringResource(R.string.backup_confirm_replace), color = MaterialTheme.colorScheme.error) } },
        )
    }
}

@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    SettingsDetailScaffold(stringResource(R.string.about_title), onBack) { padding ->
        SettingsAboutContent(Modifier.padding(padding))
    }
}

@Composable
internal fun SettingsAboutContent(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("about-content"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppLogo(Modifier.size(132.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.about_description),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    stringResource(R.string.about_tagline),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.about_privacy)) {
                InfoLine(stringResource(R.string.about_local_data))
                InfoLine(stringResource(R.string.about_backup))
                InfoLine(stringResource(R.string.about_currencies))
            }
        }
        item {
            SectionCard(title = stringResource(R.string.about_vndb)) {
                InfoLine(stringResource(R.string.about_vndb_api))
                InfoLine(stringResource(R.string.about_snapshots))
                InfoLine(stringResource(R.string.about_disclaimer))
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.about_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
}
