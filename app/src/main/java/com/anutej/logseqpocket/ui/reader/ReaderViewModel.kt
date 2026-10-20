package com.anutej.logseqpocket.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anutej.logseqpocket.data.db.BlockEntity
import com.anutej.logseqpocket.data.db.BlockHit
import com.anutej.logseqpocket.data.db.BlockTagRow
import com.anutej.logseqpocket.data.db.PageEntity
import com.anutej.logseqpocket.data.db.TagEntity
import com.anutej.logseqpocket.data.repo.NotesRepository
import com.anutej.logseqpocket.domain.PropChip
import com.anutej.logseqpocket.domain.PropertyFormatter
import com.anutej.logseqpocket.domain.RefIndex
import com.anutej.logseqpocket.domain.markdown.Inline
import com.anutej.logseqpocket.domain.markdown.MarkdownParser
import com.anutej.logseqpocket.domain.markdown.MdBlock
import com.anutej.logseqpocket.domain.markdown.collectRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class BlockRowUi(
    val block: BlockEntity,
    val md: List<MdBlock>,
    val tags: List<BlockTagRow>,
    val props: List<PropChip>,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

data class ReaderUi(
    val loading: Boolean = true,
    val page: PageEntity? = null,
    val rows: List<BlockRowUi> = emptyList(),
    val totalBlocks: Int = 0,
    val pageTags: List<TagEntity> = emptyList(),
    val backlinks: List<BlockHit> = emptyList(),
    val refs: RefIndex = RefIndex.EMPTY,
    val blockRefs: Map<String, BlockEntity> = emptyMap(),
    val focusBlock: String? = null,
)

/** Parses each block once; flattening the outline into visible rows keeps huge pages fast. */
class ReaderViewModel(
    private val notes: NotesRepository,
    pageUuid: String,
    private val focusBlock: String?,
) : ViewModel() {

    private val collapsed = MutableStateFlow<Set<String>?>(null)
    private val parsed = HashMap<String, Pair<String, List<MdBlock>>>()
    private var blockRefCache: Map<String, BlockEntity> = emptyMap()
    @Volatile private var lastBlocks: List<BlockEntity> = emptyList()

    val ui: StateFlow<ReaderUi> = combine(
        notes.pageContent(pageUuid),
        notes.refIndex,
        notes.backlinks(pageUuid),
        collapsed,
    ) { content, refs, backlinks, collapsedSet ->
        val blocks = content.blocks
        lastBlocks = blocks
        val initial = collapsedSet ?: initialCollapsed(blocks).also { collapsed.value = it }
        val md = blocks.associate { b -> b.uuid to parse(b) }

        val wantedRefs = md.values.asSequence().flatMap { collectRefs(it).asSequence() }
            .filterIsInstance<Inline.BlockRef>().map { it.uuid }.toSet()
        if (!blockRefCache.keys.containsAll(wantedRefs)) blockRefCache = notes.blocksByUuid(wantedRefs)

        val rows = ArrayList<BlockRowUi>(blocks.size)
        var hideDeeperThan = Int.MAX_VALUE
        for (b in blocks) {
            if (b.depth > hideDeeperThan) continue
            hideDeeperThan = Int.MAX_VALUE
            val hasChildren = b.childCount > 0
            val expanded = !(hasChildren && b.uuid in initial)
            if (!expanded) hideDeeperThan = b.depth
            rows += BlockRowUi(
                block = b,
                md = md.getValue(b.uuid),
                tags = content.blockTags[b.uuid].orEmpty(),
                props = PropertyFormatter.format(b.propertiesJson, refs),
                hasChildren = hasChildren,
                expanded = expanded,
            )
        }
        ReaderUi(
            loading = false,
            page = content.page,
            rows = rows,
            totalBlocks = blocks.size,
            pageTags = content.pageTags,
            backlinks = backlinks,
            refs = refs,
            blockRefs = blockRefCache,
            focusBlock = focusBlock,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUi())

    private fun parse(b: BlockEntity): List<MdBlock> {
        val cached = parsed[b.uuid]
        if (cached != null && cached.first == b.content) return cached.second
        return MarkdownParser.parse(b.content).also { parsed[b.uuid] = b.content to it }
    }

    /** Starts from Logseq's own collapsed flags, but opens the path to a block we were asked to show. */
    private fun initialCollapsed(blocks: List<BlockEntity>): Set<String> {
        val set = blocks.filter { it.collapsed && it.childCount > 0 }.mapTo(HashSet()) { it.uuid }
        if (focusBlock != null) {
            val byUuid = blocks.associateBy { it.uuid }
            var cur = byUuid[focusBlock]?.parentUuid
            while (cur != null) { set.remove(cur); cur = byUuid[cur]?.parentUuid }
        }
        return set
    }

    fun toggle(uuid: String) = collapsed.update { s -> s?.let { if (uuid in it) it - uuid else it + uuid } }

    fun expandAll() = collapsed.update { emptySet() }

    fun collapseAll() = collapsed.update { lastBlocks.filter { it.childCount > 0 }.mapTo(HashSet()) { it.uuid } }
}
