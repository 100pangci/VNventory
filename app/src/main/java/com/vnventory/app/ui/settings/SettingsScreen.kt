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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.ui.components.OperationError
import com.vnventory.app.ui.components.PageHeader
import com.vnventory.app.ui.components.ShelfIconTile
import androidx.compose.ui.res.stringResource
import com.vnventory.app.R

@Composable
fun SettingsScreen(
    onPreferences: () -> Unit = {},
    onData: () -> Unit = {},
    onAbout: () -> Unit = {},
    onShops: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    SettingsHomeContent(defaultCurrency, onPreferences, onData, onAbout, error = { OperationError(viewModel) }, onShops = onShops)
}

@Composable
internal fun SettingsHomeContent(
    defaultCurrency: String,
    onPreferences: () -> Unit,
    onData: () -> Unit,
    onAbout: () -> Unit,
    error: @Composable () -> Unit = {},
    onShops: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { error() }
        item { PageHeader(stringResource(R.string.nav_settings), stringResource(R.string.settings_hint), eyebrow = stringResource(R.string.settings_eyebrow)) }
        item { SettingsGroupTitle(stringResource(R.string.settings_collection)) }
        item {
            SettingsEntry(R.drawable.ic_ui_preferences, stringResource(R.string.settings_preferences), stringResource(R.string.settings_currency_summary, defaultCurrency), onPreferences)
        }
        item { SettingsEntry(R.drawable.ic_ui_shop, stringResource(R.string.shops_settings), stringResource(R.string.shops_settings_hint), onShops) }
        item { SettingsDivider() }
        item { SettingsGroupTitle(stringResource(R.string.settings_data)) }
        item {
            SettingsEntry(R.drawable.ic_ui_backup, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_hint), onData)
        }
        item { SettingsDivider() }
        item { SettingsGroupTitle(stringResource(R.string.settings_app)) }
        item {
            SettingsEntry(R.drawable.ic_ui_info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_hint), onAbout)
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
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun SettingsEntry(@DrawableRes icon: Int, title: String, supporting: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { ShelfIconTile(icon, size = 40.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
