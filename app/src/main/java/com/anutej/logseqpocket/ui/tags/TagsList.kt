package com.anutej.logseqpocket.ui.tags

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Tag
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.TagEntity
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.KindBadge
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.theme.PocketTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TagItem(val tag: TagEntity, val similar: List<String>)

/** Groups spellings that differ only in punctuation/case (e.g. array-hashing vs arrayhashing). */
fun similarityKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

class TagsViewModel(notes: NotesRepository) : ViewModel() {
    val tags = notes.tags.map { list ->
        val groups = list.groupBy { similarityKey(it.name) }
        list.map { t -> TagItem(t, groups[similarityKey(t.name)].orEmpty().filter { it.tagKey != t.tagKey }.map { it.name }) }
    }.map<List<TagItem>, List<TagItem>?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun TagsList(padding: PaddingValues, onOpenTag: (String) -> Unit) {
    val vm = pocketViewModel { TagsViewModel(it.notes) }
    val all by vm.tags.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val list = all
    if (list == null) { LoadingState(); return }
    val shown = remember(list, query) {
        val q = query.trim().lowercase().removePrefix("#")
        list.filter { q.isEmpty() || it.tag.nameLower.contains(q) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Filter tags") }, leadingIcon = { Icon(Icons.Outlined.FilterList, null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (shown.isEmpty()) item { EmptyState(Icons.Outlined.Tag, "No tags", "Tags and classes from your notes appear here.") }
        items(shown, key = { it.tag.tagKey }) { item -> TagRowItem(item) { onOpenTag(item.tag.tagKey) } }
    }
}

@Composable
private fun TagRowItem(item: TagItem, onClick: () -> Unit) {
    val t = item.tag
    val colors = PocketTheme.colors
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("#${t.name}", style = MaterialTheme.typography.titleSmall, color = colors.tagChipText)
                val counts = listOfNotNull(
                    t.blockCount.takeIf { it > 0 }?.let { "$it block${if (it == 1) "" else "s"}" },
                    t.pageCount.takeIf { it > 0 }?.let { "$it page${if (it == 1) "" else "s"}" },
                ).joinToString(" · ").ifEmpty { "Not used yet" }
                Text(counts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.similar.isNotEmpty()) {
                    Text(
                        "Similar: " + item.similar.joinToString { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            KindBadge(if (t.pageUuid == null) "Built-in" else "Class")
        }
    }
}
