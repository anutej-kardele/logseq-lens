package com.anutej.logseqpocket.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.repo.Deck
import com.anutej.logseqpocket.data.repo.DeckOverview
import com.anutej.logseqpocket.data.repo.StudyRepository
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.theme.PocketTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CardsViewModel(study: StudyRepository) : ViewModel() {
    val overview = study.overview.map<DeckOverview, DeckOverview?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(onStudy: (String) -> Unit) {
    val vm = pocketViewModel { CardsViewModel(it.study) }
    val overview by vm.overview.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Flashcards") }) }) { padding ->
        val o = overview
        if (o == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val all = o.decks.first()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (all.size == 0) {
                item {
                    EmptyState(
                        Icons.Outlined.Style,
                        "No flashcards found",
                        "Blocks tagged #Card in Logseq appear here. The block is the question; its nested blocks are the answer.",
                    )
                }
                return@LazyColumn
            }
            item { DeckCard(all, prominent = true) { onStudy(all.key) } }
            val categories = o.decks.drop(1)
            if (categories.isNotEmpty()) item { SectionHeader("By category") }
            items(categories, key = { it.key }) { d -> DeckCard(d) { onStudy(d.key) } }
            item {
                Text(
                    "Progress is stored only on this phone and never sent to Logseq or GitHub." +
                        if (o.orphanProgress > 0) " ${o.orphanProgress} saved results belong to cards not in the current export." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DeckCard(deck: Deck, prominent: Boolean = false, onClick: () -> Unit) {
    val colors = PocketTheme.colors
    Surface(
        Modifier.fillMaxWidth().clickable(enabled = deck.size > 0, onClick = onClick)
            .semantics { contentDescription = "${deck.title}, ${deck.size} cards, ${deck.known} known" },
        color = if (prominent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (prominent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(deck.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = buildString {
                        append("${deck.size} card${if (deck.size == 1) "" else "s"}")
                        if (deck.tagName != null) append(" · #${deck.tagName}")
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (deck.size == 0) 0f else deck.known.toFloat() / deck.size },
                modifier = Modifier.fillMaxWidth(),
                color = colors.know,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend("Known ${deck.known}", colors.know)
                Legend("Review again ${deck.again}", colors.again)
                Legend("New ${deck.unseen}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.width(8.dp).height(8.dp), color = color, shape = MaterialTheme.shapes.extraSmall) {}
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
