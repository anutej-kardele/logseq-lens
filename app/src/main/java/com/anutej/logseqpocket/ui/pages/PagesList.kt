package com.anutej.logseqpocket.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.KindBadge
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.reader.kindLabel
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PagesViewModel(notes: NotesRepository) : ViewModel() {
    val pages = notes.pages.map<List<PageSummary>, List<PageSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

enum class PageFilter(val label: String) { All("All"), Pages("Pages"), Journals("Journals"), Tags("Tags & classes") }

/** Alphabetical page list with text filter and kind filters. */
@Composable
fun PagesList(padding: PaddingValues, onOpenPage: (PageSummary) -> Unit) {
    val vm = pocketViewModel { PagesViewModel(it.notes) }
    val all by vm.pages.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PageFilter.All) }
    var hideEmpty by rememberSaveable { mutableStateOf(true) }

    val list = all
    if (list == null) { LoadingState(Modifier.padding(padding)); return }
    val shown = remember(list, query, filter, hideEmpty) {
        val q = query.trim().lowercase()
        list.filter { s ->
            val p = s.page
            val kindOk = when (filter) {
                PageFilter.All -> true
                PageFilter.Pages -> p.kind != "journal" && p.kind != "tag" && p.kind != "class"
                PageFilter.Journals -> p.kind == "journal"
                PageFilter.Tags -> p.kind == "tag" || p.kind == "class"
            }
            // Tag/class pages stay visible even when empty: they open the tag listing.
            val emptyOk = !hideEmpty || p.nonEmptyBlockCount > 0 || p.kind == "tag" || p.kind == "class"
            kindOk && emptyOk && (q.isEmpty() || p.titleLower.contains(q))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter pages") },
                leadingIcon = { Icon(Icons.Outlined.FilterList, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageFilter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                item { FilterChip(selected = hideEmpty, onClick = { hideEmpty = !hideEmpty }, label = { Text("Hide empty") }) }
            }
        }
        item {
            Text(
                Format.count(shown.size, "page"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        if (shown.isEmpty()) item { EmptyState(Icons.Outlined.Description, "No matching pages", "Try another filter.") }
        items(shown, key = { it.page.uuid }) { s -> PageRow(s) { onOpenPage(s) } }
    }
}

@Composable
private fun PageRow(s: PageSummary, onClick: () -> Unit) {
    val p = s.page
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (p.kind == "journal") Format.journalLong(p.journalDay, p.title) else p.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (p.nonEmptyBlockCount == 0) "No content" else Format.count(p.nonEmptyBlockCount, "block"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            KindBadge(kindLabel(p.kind))
        }
    }
}
