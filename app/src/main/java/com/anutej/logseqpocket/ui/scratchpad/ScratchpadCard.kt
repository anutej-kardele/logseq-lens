package com.anutej.logseqpocket.ui.scratchpad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.anutej.logseqpocket.domain.scratchpad.PadDoc

/** Share of Home's content area (between top bar and bottom tabs) the Scratchpad card may use. */
const val HOME_CARD_FRACTION = 0.35f

/** Share of the screen height the floating note (opened from the widget) may use, keyboard excluded. */
const val FLOATING_FRACTION = 0.33f

/** Keeps scrolling that reaches the end of the note inside the card instead of moving the page behind it. */
private val ConsumeLeftoverScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * The Scratchpad right on Home: tap a checkbox to tick it, tap anywhere else to type in place
 * (Enter for a new line, "[] " for a checkbox). The expand icon opens the full note.
 */
@Composable
fun ScratchpadCard(
    doc: PadDoc?,
    vm: ScratchpadViewModel,
    onOpenFull: () -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var focusedLine by remember { mutableStateOf<Long?>(null) }
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.heightIn(max = maxHeight).padding(top = 4.dp, bottom = 4.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Scratchpad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                doc?.takeIf { it.openItems > 0 }?.let {
                    Text(it.openItemsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOpenFull) { Icon(Icons.Outlined.OpenInFull, "Open full Scratchpad", Modifier.size(18.dp)) }
            }
            if (doc == null) return@Column
            PadNoteEditor(
                doc = doc,
                vm = vm,
                onFocusedLine = { focusedLine = it },
                textStyle = MaterialTheme.typography.bodyLarge,
                tailHeight = 20.dp,
                placeholder = "Tap to write · [] for a checkbox",
                modifier = Modifier.weight(1f, fill = false).nestedScroll(ConsumeLeftoverScroll).verticalScroll(rememberScrollState()),
            )
            if (focusedLine != null) {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                PadEditToolbar(focusedLine, vm, Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
