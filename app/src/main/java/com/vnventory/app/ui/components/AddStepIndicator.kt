package com.vnventory.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vnventory.app.ui.theme.ShelfMotion

@Composable
fun AddStepIndicator(step: Int, modifier: Modifier = Modifier) {
    val labels = listOf("寻找作品", "选择版本", "记录购入")
    val progress = animateFloatAsState((step + 1) / 3f, tween(ShelfMotion.Standard), label = "addProgress")
    Column(modifier.semantics { stateDescription = "第 ${step + 1} 步，共 3 步" }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                val color = animateColorAsState(
                    if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(ShelfMotion.Quick), label = "stepColor",
                )
                Text("${if (index < step) "✓" else index + 1}  $label", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = color.value)
            }
        }
        LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
    }
}
