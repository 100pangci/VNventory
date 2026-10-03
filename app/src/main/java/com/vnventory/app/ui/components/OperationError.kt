package com.vnventory.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vnventory.app.ui.ActionViewModel

@Composable
fun OperationError(viewModel: ActionViewModel) {
    val message by viewModel.actionError.collectAsStateWithLifecycle()
    message?.let {
        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp)) {
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = viewModel::clearError) { Text("关闭") }
            }
        }
    }
}
