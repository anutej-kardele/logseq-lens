package com.anutej.logseqpocket.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.BlockHit
import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.db.TagEntity
import com.anutej.logseqpocket.data.repo.Deck
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.data.repo.StudyRepository
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.home.SimpleRow
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.reader.BlockHitCard
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TagUi(
    val loading: Boolean = true,
    val tag: TagEntity? = null,
    val pages: List<PageSummary> = emptyList(),
    val blocks: List<BlockHit> = emptyList(),
    val deck: Deck? = null,
    val tagPageHasBlocks: Boolean = false,
)

class TagViewModel(notes: NotesRepository, study: StudyRepository, tagKey: String) : ViewModel() {
    val ui = combine(
        notes.tag(tagKey),
        notes.pagesForTag(tagKey),
        notes.blocksForTag(tagKey),
        study.overview,
        notes.pages,
    ) { tag, pages, blocks, overview, allPages ->
        TagUi(
            loading = false,
            tag = tag,
            pages = pages,
            blocks = blocks,
            deck = overview.decks.firstOrNull { it.key == StudyRepository.TAG_PREFIX + tagKey },
            tagPageHasBlocks = tag?.pageUuid?.let { pu -> allPages.firstOrNull { it.page.uuid == pu }?.page?.nonEmptyBlockCount ?: 0 } ?: 0 > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagUi())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(tagKey: String, onBack: () -> Unit, onNavigate: (NavRequest) -> Unit, onStudy: (String) -> Unit) {
    val vm = pocketViewModel(key = "tag:$tagKey") { TagViewModel(it.notes, it.study, tagKey) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.tag?.let { "#${it.name}" } ?: "Tag") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        val tag = ui.tag
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            tag == null -> EmptyState(Icons.Outlined.Tag, "Tag not found", "This tag isn't in the current dataset.", Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            listOf(Format.count(tag.blockCount, "tagged block"), Format.count(tag.pageCount, "tagged page"))
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.deck?.takeIf { it.size > 0 }?.let { d ->
                                FilledTonalButton(onClick = { onStudy(d.key) }) {
                                    Icon(Icons.Outlined.Style, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text("Study ${d.size} flashcard${if (d.size == 1) "" else "s"}")
                                }
                            }
                            if (ui.tagPageHasBlocks) {
                                OutlinedButton(onClick = { tag.pageUuid?.let { onNavigate(NavRequest.Page(it)) } }) {
                                    Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text("Open tag page")
                                }
                            }
                        }
                    }
                }
                if (ui.pages.isNotEmpty()) {
                    item { SectionHeader("Pages", Modifier.padding(horizontal = 20.dp)) }
                    items(ui.pages, key = { "p:" + it.page.uuid }) { p ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                            SimpleRow(p.page.title, null) { onNavigate(NavRequest.Page(p.page.uuid)) }
                        }
                    }
                }
                if (ui.blocks.isNotEmpty()) {
                    item { SectionHeader("Blocks", Modifier.padding(horizontal = 20.dp)) }
                    items(ui.blocks, key = { "b:" + it.blockUuid }) { hit ->
                        BlockHitCard(hit, onClick = { onNavigate(NavRequest.Page(hit.pageUuid, hit.blockUuid)) })
                    }
                }
                if (ui.pages.isEmpty() && ui.blocks.isEmpty()) {
                    item { EmptyState(Icons.Outlined.Tag, "Nothing tagged yet", "No pages or blocks use #${tag.name} in this export.") }
                }
            }
        }
    }
}
