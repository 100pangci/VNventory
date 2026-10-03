package com.vnventory.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.BuildConfig
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.AppLogo
import com.vnventory.app.ui.components.SectionCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailScaffold(
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
    SettingsDetailScaffold("偏好设置", onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { OperationError(viewModel) }
            item {
                SectionCard(title = "默认货币") {
                    InfoLine("添加收藏、新建订单和费用时的默认币种，每笔仍可单独修改。")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Money.commonCurrencies.forEach { code ->
                            FilterChip(
                                selected = currency == code,
                                onClick = { viewModel.setDefaultCurrency(code) },
                                label = { Text("$code（${Money.symbol(code).ifEmpty { code }}）") },
                            )
                        }
                    }
                    if (currency !in Money.commonCurrencies) InfoLine("当前默认货币：$currency")
                }
            }
        }
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
    SettingsDetailScaffold("备份与恢复", onBack, state.busy) { padding ->
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
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { error() }
        if (state.busy) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress, style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.feedback?.let { feedback -> item { Text(feedback, color = MaterialTheme.colorScheme.primary) } }
        item {
            SectionCard(title = "导出备份") {
                InfoLine("将默认货币、所有收藏、购买批次、费用和手动分摊保存为 JSON 文件。")
                InfoLine("保留标题、版本、封面链接及购买记录，不包含 VNDB 缓存或图片文件。")
                Button(onClick = onExport, enabled = !state.busy && state.pendingImport == null, modifier = Modifier.fillMaxWidth()) {
                    Text("导出备份")
                }
            }
        }
        item {
            SectionCard(title = "恢复备份") {
                InfoLine("先选择并检查备份，再决定追加还是覆盖。追加保留现有记录，但重复导入会增加重复收藏。")
                InfoLine("覆盖将替换全部收藏、订单和费用，请先导出当前数据。")
                OutlinedButton(onClick = onImport, enabled = !state.busy && state.pendingImport == null, modifier = Modifier.fillMaxWidth()) {
                    Text("选择备份文件")
                }
            }
        }
        item {
            SectionCard(title = "备份须知") {
                InfoLine("备份为未加密文本，含价格、店铺和备注等私人信息，请保存到可信位置。")
                InfoLine("系统自动备份仍然禁用；卸载或清除数据前，请将备份保存在应用之外。")
                InfoLine("应用只读写你通过系统文件选择器选定的文档，不自动上传。文件上限为 32 MiB。")
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
    error: String? = null,
) {
    val backup = state.pendingImport ?: return
    if (!state.replaceConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onDismiss() },
            title = { Text("检查备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${backup.copies.size} 盒收藏 · ${backup.orders.size} 个购买批次 · ${backup.expenses.size} 笔费用")
                    val exported = runCatching {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(backup.exportedAt).atZone(ZoneId.systemDefault()))
                    }.getOrDefault("未知")
                    InfoLine("导出时间：$exported")
                    InfoLine("追加保留现有数据，重复导入不会自动去重。覆盖会替换现有收藏、订单和费用。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.restoreCurrency, onCheckedChange = onRestoreCurrency, enabled = !state.busy)
                        Text("同时恢复默认货币（${backup.defaultCurrency}）", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.busy) Text(state.progress)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("取消") } },
            confirmButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onAppend, enabled = !state.busy) { Text("追加恢复") }
                    TextButton(onClick = onRequestReplace, enabled = !state.busy) { Text("覆盖恢复", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onCancelReplace() },
            title = { Text("覆盖现有数据？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前所有收藏、购买批次、费用和手动分摊都将被此备份替换，无法撤销。请确认已经导出当前数据。")
                    if (state.busy) Text(state.progress)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = onCancelReplace, enabled = !state.busy) { Text("返回") } },
            confirmButton = { TextButton(onClick = onReplace, enabled = !state.busy) { Text("确认覆盖", color = MaterialTheme.colorScheme.error) } },
        )
    }
}

@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    SettingsDetailScaffold("关于 VNventory", onBack) { padding ->
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
                Text("VNventory", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Galgame / Visual Novel 实体收藏管理",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    "每一盒收藏，每一笔真实成本。",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "数据与隐私") {
                InfoLine("收藏数据和配置保存在本机，不登录、不自动上传。")
                InfoLine("支持手动导出和恢复备份；卸载或清除数据前，请将备份保存在应用之外。")
                InfoLine("多币种金额不做汇率换算，按币种分开统计。")
            }
        }
        item {
            SectionCard(title = "VNDB 数据来源") {
                InfoLine("作品 / 版本元数据来自 VNDB 公共 API（api.vndb.org），仅作缓存使用。")
                InfoLine("收藏保留标题与封面链接快照，VNDB 缓存变化或清理不会改写你的购买记录。")
                InfoLine("本应用与 VNDB 官方无隶属关系，数据版权归 VNDB 及各版权方所有。")
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("开源许可 · MPL-2.0", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("为热爱留一格书架。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
}
