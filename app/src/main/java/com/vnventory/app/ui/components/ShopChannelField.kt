package com.vnventory.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vnventory.app.R

/** Presets are a shortcut, not a constraint: old names and one-off manual entries remain valid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopChannelField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.shop_channel),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val dropdownDescription = stringResource(R.string.shops_options)
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.shops_choose_hint)) },
            supportingText = if (options.isEmpty()) ({ Text(stringResource(R.string.shops_no_options_hint)) }) else null,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded, modifier = Modifier
                    .semantics { contentDescription = dropdownDescription }
                    .menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable, enabled))
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shops_clear)) },
                onClick = { onValueChange(""); expanded = false; focus.clearFocus() },
            )
            options.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onValueChange(name)
                    expanded = false
                    focus.clearFocus()
                })
            }
        }
    }
}
