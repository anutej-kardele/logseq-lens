package com.anutej.logseqpocket.ui.scratchpad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anutej.logseqpocket.domain.scratchpad.PadDoc

/** Home preview of the Scratchpad: the first few lines, tap to open the editor. */
@Composable
fun ScratchpadCard(doc: PadDoc?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val preview = doc?.lines.orEmpty().filter { it.text.isNotBlank() }.take(4)
    Surface(
        modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Scratchpad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                doc?.openItems?.takeIf { it > 0 }?.let {
                    Text("$it open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Open Scratchpad", Modifier.size(20.dp))
            }
            if (preview.isEmpty()) {
                Text(
                    "Jot down thoughts or to-dos. Stays on this phone, never synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (line.checkbox) {
                        Icon(
                            if (line.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (line.checkbox && line.checked) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
