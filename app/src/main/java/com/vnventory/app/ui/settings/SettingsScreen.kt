package com.vnventory.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.PageHeader

@Composable
fun SettingsScreen(
    onPreferences: () -> Unit = {},
    onData: () -> Unit = {},
    onAbout: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    SettingsHomeContent(defaultCurrency, onPreferences, onData, onAbout, error = { OperationError(viewModel) })
}

@Composable
internal fun SettingsHomeContent(
    defaultCurrency: String,
    onPreferences: () -> Unit,
    onData: () -> Unit,
    onAbout: () -> Unit,
    error: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { error() }
        item { PageHeader("设置", "让这座书架更符合你的收藏习惯。", eyebrow = "PREFERENCES") }
        item { SettingsGroupTitle("收藏偏好") }
        item {
            SettingsEntry(Icons.Default.Settings, "偏好设置", "默认货币 · $defaultCurrency", onPreferences)
        }
        item { SettingsDivider() }
        item { SettingsGroupTitle("数据管理") }
        item {
            SettingsEntry(Icons.Default.Refresh, "备份与恢复", "导出配置与收藏，或从 JSON 备份恢复", onData)
        }
        item { SettingsDivider() }
        item { SettingsGroupTitle("应用信息") }
        item {
            SettingsEntry(Icons.Default.Info, "关于与数据来源", "版本信息、隐私及 VNDB 数据说明", onAbout)
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun SettingsEntry(icon: ImageVector, title: String, supporting: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
