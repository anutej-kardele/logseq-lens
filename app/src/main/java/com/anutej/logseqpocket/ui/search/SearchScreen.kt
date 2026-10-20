package com.anutej.logseqpocket.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.SearchRow
import com.anutej.logseqpocket.data.repo.SearchRepository
import com.anutej.logseqpocket.data.repo.SearchResults
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.plainText
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.home.SimpleRow
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.reader.kindLabel
import com.anutej.logseqpocket.ui.theme.PocketColors
import com.anutej.logseqpocket.ui.theme.PocketTheme
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUi(val query: String = "", val searching: Boolean = false, val results: SearchResults? = null)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repo: SearchRepository) : ViewModel() {
    val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    val searchingState: StateFlow<Boolean> = searching

    val results: StateFlow<SearchResults?> = query
        .debounce(250)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.isBlank()) null else {
                searching.value = true
                try { repo.search(q) } finally { searching.value = false }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onNavigate: (NavRequest) -> Unit) {
    val vm = pocketViewModel { SearchViewModel(it.search) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searchingState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Search titles and notes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            val r = results
            if (r == null) {
                item {
                    Text(
                        "Searches everything stored on this phone. Nothing leaves the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                return@LazyColumn
            }
            if (r.pages.isEmpty() && r.blocks.isEmpty()) {
                item { EmptyState(Icons.Outlined.Search, "No matches", "Nothing in your notes matches “${r.query.trim()}”.") }
                return@LazyColumn
            }
            if (r.pages.isNotEmpty()) {
                item { SectionHeader("Pages · ${r.pages.size}") }
                items(r.pages, key = { "p:" + it.page.uuid }) { p ->
                    val title = if (p.page.kind == "journal") Format.journalLong(p.page.journalDay, p.page.title) else p.page.title
                    SimpleRow(title, kindLabel(p.page.kind)) {
                        onNavigate(if (p.page.kind == "tag" || p.page.kind == "class") NavRequest.Tag(p.page.uuid) else NavRequest.Page(p.page.uuid))
                    }
                }
            }
            if (r.blocks.isNotEmpty()) {
                item { SectionHeader("Blocks · ${r.blocks.size}${if (r.blocks.size >= 200) "+" else ""}") }
                items(r.blocks, key = { "b:" + it.blockUuid }) { hit -> BlockResult(hit, r.terms) { onNavigate(NavRequest.Page(hit.pageUuid, hit.blockUuid)) } }
            }
        }
    }
}

@Composable
private fun BlockResult(hit: SearchRow, terms: List<String>, onClick: () -> Unit) {
    val colors = PocketTheme.colors
    val snippet = remember(hit.content, terms, colors) { snippet(plainText(InlineParser.parse(hit.content)), terms, colors) }
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                if (hit.pageKind == "journal") Format.journalShort(hit.journalDay, hit.pageTitle) else hit.pageTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A window of text around the first match, with every term occurrence emphasised. */
fun snippet(text: String, terms: List<String>, colors: PocketColors, radius: Int = 80): AnnotatedString {
    val flat = text.replace('\n', ' ')
    val lower = flat.lowercase()
    val first = terms.mapNotNull { t -> lower.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: 0
    val start = (first - radius / 2).coerceAtLeast(0).let { s -> if (s > 0) flat.indexOf(' ', s).takeIf { it in s until first } ?: s else 0 }
    val end = (first + radius * 2).coerceAtMost(flat.length)
    val window = flat.substring(start, end)
    val wl = window.lowercase()
    return buildAnnotatedString {
        if (start > 0) append("… ")
        var i = 0
        while (i < window.length) {
            val hit = terms.filter { it.isNotEmpty() && wl.startsWith(it, i) }.maxByOrNull { it.length }
            if (hit != null) {
                withStyle(SpanStyle(background = colors.highlight, fontWeight = FontWeight.SemiBold)) { append(window.substring(i, i + hit.length)) }
                i += hit.length
            } else {
                append(window[i]); i++
            }
        }
        if (end < flat.length) append(" …")
    }
}
