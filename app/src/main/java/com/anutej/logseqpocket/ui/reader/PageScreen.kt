package com.anutej.logseqpocket.ui.reader

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.data.db.BlockHit
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.plainText
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.KindBadge
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.components.RenderContext
import com.anutej.logseqpocket.ui.components.SectionHeader
import com.anutej.logseqpocket.ui.components.TagRow
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    pageUuid: String,
    focusBlock: String?,
    onBack: () -> Unit,
    onNavigate: (NavRequest) -> Unit,
) {
    val vm = pocketViewModel(key = "page:$pageUuid:$focusBlock") { ReaderViewModel(it.notes, pageUuid, focusBlock) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    val page = ui.page
    val title = page?.let { if (it.kind == "journal") Format.journalShort(it.journalDay, it.title) else it.title } ?: ""

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Expand all") },
                            leadingIcon = { Icon(Icons.Outlined.UnfoldMore, null) },
                            onClick = { vm.expandAll(); menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Collapse all") },
                            leadingIcon = { Icon(Icons.Outlined.UnfoldLess, null) },
                            onClick = { vm.collapseAll(); menu = false },
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            page == null -> EmptyState(
                Icons.Outlined.Description,
                "Page not available",
                "This page isn't in the current dataset. It may have been removed in a newer import.",
                Modifier.padding(padding),
            )
            else -> PageBody(ui, padding, vm::toggle, onNavigate)
        }
    }
}

@Composable
private fun PageBody(
    ui: ReaderUi,
    padding: PaddingValues,
    onToggle: (String) -> Unit,
    onNavigate: (NavRequest) -> Unit,
) {
    val page = ui.page ?: return
    val listState = rememberLazyListState()
    val ctx = remember(ui.refs, ui.blockRefs, onNavigate) { RenderContext(ui.refs, ui.blockRefs, onNavigate) }
    var focusConsumed by rememberSaveable { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ui.focusBlock, ui.rows.size) {
        val target = ui.focusBlock ?: return@LaunchedEffect
        if (focusConsumed || ui.rows.isEmpty()) return@LaunchedEffect
        val index = ui.rows.indexOfFirst { it.block.uuid == target }
        if (index >= 0) {
            focusConsumed = true
            listState.scrollToItem(index + 1, scrollOffset = -120)
            highlighted = target
            delay(1800)
            highlighted = null
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
    ) {
        item(key = "header") { PageHeader(ui, onNavigate) }
        if (ui.rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No blocks on this page in the export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        items(ui.rows, key = { it.block.uuid }, contentType = { "block" }) { row ->
            BlockRow(
                row = row,
                ctx = ctx,
                onToggle = { onToggle(row.block.uuid) },
                onTagClick = { onNavigate(NavRequest.Tag(it)) },
                highlighted = highlighted == row.block.uuid,
            )
        }
        if (ui.backlinks.isNotEmpty()) {
            item(key = "backlinks-h") {
                SectionHeader("Linked references · ${ui.backlinks.size}", Modifier.padding(horizontal = 20.dp))
            }
            items(ui.backlinks, key = { "bl:" + it.blockUuid }) { hit ->
                BlockHitCard(hit, onClick = { onNavigate(NavRequest.Page(hit.pageUuid, hit.blockUuid)) })
            }
        }
    }
}

@Composable
private fun PageHeader(ui: ReaderUi, onNavigate: (NavRequest) -> Unit) {
    val page = ui.page ?: return
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
        val journal = page.kind == "journal"
        Text(
            if (journal) Format.journalLong(page.journalDay, page.title) else page.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindBadge(kindLabel(page.kind))
            Text(
                Format.count(ui.totalBlocks, "block"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (journal) Format.relativeDay(page.journalDay)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (ui.pageTags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TagRow(ui.pageTags.map { t -> t.name to { onNavigate(NavRequest.Tag(t.tagKey)) } })
        }
        if (page.kind == "tag" || page.kind == "class") {
            TextButton(onClick = { onNavigate(NavRequest.Tag(page.uuid)) }, contentPadding = PaddingValues(0.dp)) {
                Text("Show everything tagged #${page.title}")
            }
        }
    }
}

fun kindLabel(kind: String): String = when (kind) {
    "journal" -> "Journal"
    "tag" -> "Tag"
    "class" -> "Class"
    "page" -> "Page"
    "property" -> "Property"
    else -> kind.replaceFirstChar { it.uppercase() }
}

/** A block shown out of context (tags, backlinks, search) with its page label. */
@Composable
fun BlockHitCard(hit: BlockHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = remember(hit.content) { plainText(InlineParser.parse(hit.content)).replace('\n', ' ').trim() }
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
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
            Text(
                text.ifEmpty { "(empty block)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
