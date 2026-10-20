package com.anutej.logseqpocket.ui.scratchpad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anutej.logseqpocket.domain.scratchpad.PadDoc

private const val CARD_LINES = 8

/**
 * The Scratchpad right on Home: tap a checkbox to tick it, tap anywhere else to type in place
 * (Enter for a new line, "[] " for a checkbox). The expand icon opens the full note.
 */
@Composable
fun ScratchpadCard(doc: PadDoc?, vm: ScratchpadViewModel, onOpenFull: () -> Unit, modifier: Modifier = Modifier) {
    var focusedLine by remember { mutableStateOf<Long?>(null) }
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Scratchpad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                doc?.openItems?.takeIf { it > 0 }?.let {
                    Text("$it open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOpenFull) { Icon(Icons.Outlined.OpenInFull, "Open full Scratchpad", Modifier.size(18.dp)) }
            }
            if (doc == null) return@Column
            val hidden = doc.lines.size - CARD_LINES
            if (hidden > 0) {
                Text(
                    "$hidden earlier line${if (hidden == 1) "" else "s"} · open full note",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onOpenFull).padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            PadNoteEditor(
                doc = doc,
                vm = vm,
                lines = doc.recentLines(CARD_LINES),
                onFocusedLine = { focusedLine = it },
                textStyle = MaterialTheme.typography.bodyLarge,
                tailHeight = 20.dp,
                placeholder = "Tap to jot something down",
            )
            if (focusedLine != null) {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                PadEditToolbar(focusedLine, vm, Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
