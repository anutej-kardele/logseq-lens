package com.anutej.logseqpocket.ui.journals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.home.JournalRow
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class JournalsViewModel(notes: NotesRepository) : ViewModel() {
    val journals = notes.journals.map<List<PageSummary>, List<PageSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalsScreen(onOpen: (String) -> Unit) {
    val vm = pocketViewModel { JournalsViewModel(it.notes) }
    val all by vm.journals.collectAsStateWithLifecycle()
    var hideEmpty by rememberSaveable { mutableStateOf(true) }

    Scaffold(topBar = { TopAppBar(title = { Text("Journals") }) }) { padding ->
        val list = all
        if (list == null) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        val shown = if (hideEmpty) list.filter { it.page.nonEmptyBlockCount > 0 } else list
        val groups = shown.groupBy { Format.monthHeader(it.page.journalDay) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                FilterChip(
                    selected = hideEmpty,
                    onClick = { hideEmpty = !hideEmpty },
                    label = { Text(if (hideEmpty) "Hiding ${list.size - shown.size} empty days" else "Hide empty days") },
                )
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.CalendarMonth, "No journals", "Imported journals will appear here, newest first.")
                }
            }
            groups.forEach { (month, entries) ->
                stickyHeader(key = "m:$month") {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                        Text(
                            month,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp),
                        )
                    }
                }
                items(entries, key = { it.page.uuid }) { j -> JournalRow(j) { onOpen(j.page.uuid) } }
            }
        }
    }
}
