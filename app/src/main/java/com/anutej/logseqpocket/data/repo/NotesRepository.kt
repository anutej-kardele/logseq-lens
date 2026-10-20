package com.anutej.logseqpocket.data.repo

import com.anutej.logseqpocket.data.db.BlockEntity
import com.anutej.logseqpocket.data.db.BlockHit
import com.anutej.logseqpocket.data.db.BlockTagRow
import com.anutej.logseqpocket.data.db.DatasetMetaEntity
import com.anutej.logseqpocket.data.db.PageEntity
import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.db.TagEntity
import com.anutej.logseqpocket.domain.RefIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class PageContent(
    val page: PageEntity?,
    val blocks: List<BlockEntity>,
    val blockTags: Map<String, List<BlockTagRow>>,
    val pageTags: List<TagEntity>,
)

class NotesRepository(private val db: PocketDatabase) {
    private val dao = db.notesDao()

    val meta: Flow<DatasetMetaEntity?> = dao.observeMeta()
    val journals: Flow<List<PageSummary>> = dao.observeJournals()
    val pages: Flow<List<PageSummary>> = dao.observePages()
    val tags: Flow<List<TagEntity>> = dao.observeTags()

    fun recentJournals(limit: Int = 6) = dao.observeRecentJournals(limit)
    fun recentlyUpdated(limit: Int = 6) = dao.observeRecentlyUpdated(limit)

    fun pageContent(uuid: String): Flow<PageContent> = combine(
        dao.observePage(uuid),
        dao.observeBlocks(uuid),
        dao.observeBlockTagsForPage(uuid),
        dao.observePageTags(uuid),
    ) { page, blocks, tags, pageTags ->
        PageContent(page, blocks, tags.groupBy { it.blockUuid }, pageTags)
    }

    fun backlinks(pageUuid: String): Flow<List<BlockHit>> = dao.observeBacklinks(pageUuid)
    fun tag(tagKey: String): Flow<TagEntity?> = dao.observeTag(tagKey)
    fun blocksForTag(tagKey: String): Flow<List<BlockHit>> = dao.observeBlocksForTag(tagKey)
    fun pagesForTag(tagKey: String): Flow<List<PageSummary>> = dao.observePagesForTag(tagKey)

    suspend fun blocksByUuid(uuids: Collection<String>): Map<String, BlockEntity> =
        if (uuids.isEmpty()) emptyMap() else uuids.chunked(500).flatMap { dao.blocksByUuid(it) }.associateBy { it.uuid }

    suspend fun pageOfBlock(uuid: String): String? = dao.pageOfBlock(uuid)

    /** Descendants of [block] in outline order. */
    suspend fun subtree(block: BlockEntity): List<BlockEntity> =
        dao.blocksAfter(block.pageUuid, block.preorder).takeWhile { it.depth > block.depth }

    /** Page/tag lookup that refreshes whenever a new dataset is imported. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val refIndex: Flow<RefIndex> = meta
        .map { it?.contentSha256 to it?.importedAt }
        .distinctUntilChanged { a, b -> a.first == b.first }
        .mapLatest { RefIndex(dao.pageIndex(), dao.allTags()) }
}
