package com.vnventory.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { error() }
        item { PageHeader(stringResource(R.string.nav_settings), subtitle = null, eyebrow = stringResource(R.string.app_name)) }
        item {
            SettingsGroup(stringResource(R.string.settings_collection)) {
                SettingsEntry(R.drawable.ic_ui_preferences, stringResource(R.string.settings_preferences), stringResource(R.string.settings_currency_summary, defaultCurrency), onPreferences)
                HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                SettingsEntry(R.drawable.ic_ui_shop, stringResource(R.string.shops_settings), stringResource(R.string.shops_settings_hint), onShops)
            }
        }
        item {
            SettingsGroup(stringResource(R.string.settings_data)) {
                SettingsEntry(R.drawable.ic_ui_backup, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_hint), onData)
            }
        }
        item {
            SettingsGroup(stringResource(R.string.settings_app)) {
                SettingsEntry(R.drawable.ic_ui_info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_hint), onAbout)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SettingsEntry(@DrawableRes icon: Int, title: String, supporting: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ShelfIconTile(icon, size = 36.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
