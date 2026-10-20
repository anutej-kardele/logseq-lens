package com.anutej.logseqpocket.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportState

@Composable
fun ImportStatusBanner(state: ImportState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Running -> MessageBanner(
            "Importing ${state.label}…",
            modifier,
            icon = Icons.Outlined.Info,
            trailing = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
        )
        is ImportState.Finished -> when (val o = state.outcome) {
            is ImportOutcome.Imported -> MessageBanner(
                buildString {
                    append("Imported ${o.stats.pages} pages, ${o.stats.blocks} blocks, ${o.stats.cards} flashcards.")
                    if (o.stats.progressReattached > 0) append(" Kept progress for ${o.stats.progressReattached} edited cards.")
                    if (o.warnings.isNotEmpty()) append(" Note: ").append(o.warnings.joinToString("; ")).append('.')
                },
                modifier,
                icon = Icons.Outlined.CheckCircle,
                onDismiss = onDismiss,
            )
            ImportOutcome.Unchanged -> MessageBanner(
                "Already up to date — the file matches your current notes.",
                modifier,
                icon = Icons.Outlined.CheckCircle,
                onDismiss = onDismiss,
            )
            is ImportOutcome.Failed -> MessageBanner(
                o.error.userMessage + " Your existing notes were kept.",
                modifier,
                isError = true,
                icon = Icons.Outlined.ErrorOutline,
                onDismiss = onDismiss,
            )
        }
    }
}
