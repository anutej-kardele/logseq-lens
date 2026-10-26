package com.anutej.logseqpocket.data.importer

import com.anutej.logseqpocket.data.db.BlockEntity
import com.anutej.logseqpocket.data.db.BlockRefEntity
import com.anutej.logseqpocket.data.db.BlockTagEntity
import com.anutej.logseqpocket.data.db.CardCategoryEntity
import com.anutej.logseqpocket.data.db.CardEntity
import com.anutej.logseqpocket.data.db.DeckEntity
import com.anutej.logseqpocket.data.db.PageEntity
import com.anutej.logseqpocket.data.db.PageTagEntity
import com.anutej.logseqpocket.data.db.TagEntity
import com.anutej.logseqpocket.data.json.BlockDto
import com.anutej.logseqpocket.data.json.EntityRefDto
import com.anutej.logseqpocket.data.json.ExportDto
import com.anutej.logseqpocket.data.json.ExportParser
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.json.ImportException
import com.anutej.logseqpocket.data.json.PageDto
import com.anutej.logseqpocket.domain.cards.CardIdentity
import com.anutej.logseqpocket.domain.cards.CardRules
import com.anutej.logseqpocket.domain.markdown.Inline
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.MarkdownParser
import com.anutej.logseqpocket.domain.markdown.collectRefs
import com.anutej.logseqpocket.domain.markdown.isUuid
import com.anutej.logseqpocket.domain.markdown.plainText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Flat, database-ready representation of one validated export. */
data class ImportBundle(
    val graph: String,
    val schemaVersion: Int,
    val source: String?,
    val logseqSchema: String?,
    val declaredPageCount: Int?,
    val declaredBlockCount: Int?,
    val excludedSystemViewBlocks: Int?,
    val pages: List<PageEntity>,
    val blocks: List<BlockEntity>,
    val tags: List<TagEntity>,
    val blockTags: List<BlockTagEntity>,
    val pageTags: List<PageTagEntity>,
    val refs: List<BlockRefEntity>,
    val cards: List<CardEntity>,
    val cardCategories: List<CardCategoryEntity>,
    val decks: List<DeckEntity>,
    val hasTimestamps: Boolean,
    val warnings: List<String>,
)

/**
 * Validates an [ExportDto] and converts its recursive page/block tree into flat rows.
 *
 * Throws [ImportException] for anything that would make the dataset unsafe to display; softer
 * issues (count mismatches, unparseable dates) become warnings.
 */
object GraphMapper {
    val SUPPORTED_SCHEMA_VERSIONS = setOf(1)
    const val MAX_DEPTH = 128

    private val emptyProps = JsonObject(emptyMap())
    private val deckQueryRe = Regex("""^\s*\(\s*tags\s+(?:\[\[([^\]]+)]]|#?([^\s()\[\]]+))\s*\)\s*$""")

    fun map(dto: ExportDto): ImportBundle {
        val version = dto.schemaVersion ?: invalid("schemaVersion is missing")
        if (version !in SUPPORTED_SCHEMA_VERSIONS) {
            throw ImportException(ImportError.UnsupportedSchema(version, SUPPORTED_SCHEMA_VERSIONS))
        }
        val graph = dto.graph?.trim().orEmpty().ifEmpty { invalid("graph name is missing") }
        val pageDtos = dto.pages ?: invalid("pages list is missing")
        if (pageDtos.isEmpty()) invalid("the export contains no pages")

        val warnings = mutableListOf<String>()
        var badJournalDays = 0
        var untitledPages = 0

        // ---- Pages ----
        val pageByUuid = LinkedHashMap<String, PageEntity>()
        val pageIndexByUuid = HashMap<String, Int>()
        val pageInputs = ArrayList<Pair<PageEntity, PageDto>>(pageDtos.size)
        pageDtos.forEachIndexed { index, p ->
            val uuid = p.uuid?.trim()?.lowercase().orEmpty().ifEmpty { invalid("page #${index + 1} has no uuid") }
            var journalDay = parseJournalDay(p.journalDay)
            val kind = (p.kind?.trim()?.lowercase()?.ifEmpty { null })
                ?: if (journalDay != null) "journal" else "page"
            // A page UUID is one Logseq entity. Two records claiming it (e.g. a class and a separate page) mean
            // the export split one entity in two; reject rather than guess which record is right.
            pageIndexByUuid[uuid]?.let { first ->
                invalid(
                    "pages #${first + 1} (${pageByUuid.getValue(uuid).kind}) and #${index + 1} ($kind) share uuid $uuid; " +
                        "the export lists one entity twice"
                )
            }
            // A page with a valid UUID but no title (e.g. a UUID-only page) keeps its content under a
            // deterministic placeholder title instead of failing the whole import.
            val title = (p.title?.takeIf { it.isNotBlank() } ?: p.name?.takeIf { it.isNotBlank() })
                ?: "Untitled page (${uuid.take(8)})".also { untitledPages++ }
            val name = p.name?.takeIf { it.isNotBlank() } ?: title
            if (kind == "journal" && journalDay == null) {
                journalDay = parseIsoDay(title)
                if (journalDay == null) badJournalDays++
            }
            val entity = PageEntity(
                uuid = uuid,
                entityId = p.entityId,
                name = name,
                nameLower = name.lowercase(),
                title = title,
                titleLower = title.lowercase(),
                kind = kind,
                journalDay = journalDay,
                createdAt = parseTimestamp(p.createdAt),
                updatedAt = parseTimestamp(p.updatedAt),
                propertiesJson = (p.properties ?: emptyProps).toString(),
                blockCount = 0,
                nonEmptyBlockCount = 0,
            )
            pageByUuid[uuid] = entity
            pageIndexByUuid[uuid] = index
            pageInputs += entity to p
        }
        if (badJournalDays > 0) warnings += "$badJournalDays journal page(s) have no readable date"
        if (untitledPages > 0) warnings += "$untitledPages page(s) had no title and are shown as “Untitled page”"
        val pageUuidByName = HashMap<String, String>()
        for (p in pageByUuid.values) {
            pageUuidByName.putIfAbsent(p.nameLower, p.uuid)
            pageUuidByName.putIfAbsent(p.titleLower, p.uuid)
        }

        // ---- Tags ----
        val tags = LinkedHashMap<String, TagAcc>()
        val tagKeyByLowerName = HashMap<String, String>()
        fun tagKeyOf(ref: EntityRefDto): String? {
            val uuid = ref.uuid?.trim()?.lowercase()?.ifEmpty { null }
            val name = ref.name?.trim()?.ifEmpty { null }
            if (uuid == null && name == null) return null
            // A bare name reuses an existing tag of that name (e.g. a class page) instead of duplicating it.
            val key = uuid ?: name!!.lowercase().let { tagKeyByLowerName[it] ?: "name:$it" }
            val acc = tags.getOrPut(key) {
                val page = uuid?.let { pageByUuid[it] }
                TagAcc(key, name ?: page?.title ?: "Untitled tag", page?.uuid).also {
                    tagKeyByLowerName.putIfAbsent(it.name.lowercase(), key)
                }
            }
            if (acc.name == "Untitled tag" && name != null) acc.name = name
            return key
        }
        for ((page, _) in pageInputs) if (page.kind == "tag" || page.kind == "class") {
            tags.getOrPut(page.uuid) { TagAcc(page.uuid, page.title, page.uuid) }
            tagKeyByLowerName.putIfAbsent(page.titleLower, page.uuid)
        }

        // ---- Blocks (iterative DFS; children sorted by order key when every sibling has one) ----
        val blocks = ArrayList<BlockEntity>()
        val blockTags = ArrayList<BlockTagEntity>()
        val pageTags = ArrayList<PageTagEntity>()
        val parsedRefs = ArrayList<Pair<String, List<Inline>>>()
        val blockUuids = HashSet<String>()
        val blockPage = HashMap<String, String>()
        val seenBlockTags = HashSet<String>()
        val seenPageTags = HashSet<String>()
        val descendantsByBlock = HashMap<String, IntRange>()
        val updatedPages = ArrayList<PageEntity>(pageInputs.size)
        var hasTimestamps = false

        for ((page, dto) in pageInputs) {
            if (page.createdAt != null || page.updatedAt != null) hasTimestamps = true
            dto.tags.orEmpty().forEach { ref ->
                tagKeyOf(ref)?.let { key ->
                    if (seenPageTags.add(page.uuid + "\u0000" + key)) {
                        pageTags += PageTagEntity(page.uuid, key)
                        tags.getValue(key).pages += page.uuid
                    }
                }
            }
            var preorder = 0
            var nonEmpty = 0
            val startIndex = blocks.size
            // Stack frames: block, parent uuid, depth, sibling index. Pushed in reverse to pop in order.
            data class Frame(val dto: BlockDto, val parent: String?, val depth: Int, val sibling: Int)
            val stack = ArrayDeque<Frame>()
            sortedChildren(dto.blocks.orEmpty()).withIndex().reversed().forEach { (idx, b) -> stack.addLast(Frame(b, null, 0, idx)) }
            // Track open ancestors to compute descendant ranges (for card answers).
            val openAncestors = ArrayDeque<Pair<String, Int>>() // uuid to depth
            val rangeStart = HashMap<String, Int>()

            while (stack.isNotEmpty()) {
                val f = stack.removeLast()
                if (f.depth >= MAX_DEPTH) invalid("block nesting deeper than $MAX_DEPTH levels on page \"${page.title.take(40)}\"")
                val uuid = f.dto.uuid?.trim()?.lowercase().orEmpty()
                    .ifEmpty { invalid("a block on page \"${page.title.take(40)}\" has no uuid") }
                if (!blockUuids.add(uuid) || uuid in pageByUuid) invalid("duplicate block uuid found")

                while (openAncestors.isNotEmpty() && openAncestors.last().second >= f.depth) {
                    val (done, _) = openAncestors.removeLast()
                    descendantsByBlock[done] = rangeStart.getValue(done) until blocks.size
                }

                val children = sortedChildren(f.dto.children.orEmpty())
                val content = f.dto.content ?: ""
                if (content.isNotBlank()) nonEmpty++
                val created = parseTimestamp(f.dto.createdAt)
                val updated = parseTimestamp(f.dto.updatedAt)
                if (created != null || updated != null) hasTimestamps = true
                blocks += BlockEntity(
                    uuid = uuid,
                    pageUuid = page.uuid,
                    parentUuid = f.parent,
                    depth = f.depth,
                    siblingIndex = f.sibling,
                    preorder = preorder++,
                    orderKey = f.dto.order,
                    content = content,
                    collapsed = f.dto.collapsed ?: false,
                    childCount = children.size,
                    propertiesJson = (f.dto.properties ?: emptyProps).toString(),
                    createdAt = created,
                    updatedAt = updated,
                )
                blockPage[uuid] = page.uuid
                openAncestors.addLast(uuid to f.depth)
                rangeStart[uuid] = blocks.size

                f.dto.tags.orEmpty().forEachIndexed { pos, ref ->
                    val key = tagKeyOf(ref) ?: return@forEachIndexed
                    if (seenBlockTags.add(uuid + "\u0000" + key)) {
                        blockTags += BlockTagEntity(uuid, key, pos)
                        tags.getValue(key).blocks++
                    }
                }
                if (content.isNotEmpty()) parsedRefs += uuid to collectRefs(MarkdownParser.parse(content))

                children.withIndex().reversed().forEach { (idx, c) -> stack.addLast(Frame(c, uuid, f.depth + 1, idx)) }
            }
            while (openAncestors.isNotEmpty()) {
                val (done, _) = openAncestors.removeLast()
                descendantsByBlock[done] = rangeStart.getValue(done) until blocks.size
            }
            updatedPages += page.copy(blockCount = blocks.size - startIndex, nonEmptyBlockCount = nonEmpty)
        }

        // ---- References parsed from text ----
        val tagKeyByName = HashMap<String, String>()
        for (t in tags.values) tagKeyByName.putIfAbsent(t.name.lowercase(), t.key)
        val refs = ArrayList<BlockRefEntity>()
        for ((blockUuid, list) in parsedRefs) {
            for (r in list) {
                val (kind, target) = when (r) {
                    is Inline.PageRef -> "page" to r.target
                    is Inline.TagRef -> "tag" to r.target
                    is Inline.BlockRef -> "block" to r.uuid
                    else -> continue
                }
                val targetPage = when (kind) {
                    "block" -> blockPage[target.lowercase()]
                    else -> if (isUuid(target)) {
                        pageByUuid[target.lowercase()]?.uuid
                    } else {
                        pageUuidByName[target.lowercase()]
                    }
                }
                refs += BlockRefEntity(blockUuid = blockUuid, kind = kind, target = target, targetPageUuid = targetPage)
            }
        }

        // ---- Flashcards ----
        val cardTagKeys = tags.values.filter { CardRules.isCardTag(it.name) }.map { it.key }.toSet()
        val deckTagKeys = tags.values.filter { CardRules.isDeckTag(it.name) }.map { it.key }.toSet()
        val tagsByBlock = blockTags.groupBy({ it.blockUuid }, { it.tagKey })
        val identity = CardIdentity()
        val cards = ArrayList<CardEntity>()
        val cardCategories = ArrayList<CardCategoryEntity>()
        val decks = LinkedHashMap<String, DeckEntity>()
        val pageOrder = updatedPages.sortedWith(
            compareBy<PageEntity>({ it.journalDay == null }, { it.journalDay ?: 0 }, { it.titleLower })
        ).withIndex().associate { (i, p) -> p.uuid to i }
        val pageMap = updatedPages.associateBy { it.uuid }
        val blocksSorted = blocks.sortedWith(compareBy({ pageOrder[it.pageUuid] }, { it.preorder }))
        for (b in blocksSorted) {
            val keys = tagsByBlock[b.uuid].orEmpty()
            if (keys.any { it in deckTagKeys }) {
                deckTarget(b.propertiesJson)?.let { target ->
                    val tagKey = if (isUuid(target)) target.lowercase().takeIf { it in tags } else tagKeyByName[target.lowercase()]
                    val title = plainText(InlineParser.parse(b.content)).trim()
                    if (tagKey != null && title.isNotEmpty()) decks.putIfAbsent(tagKey, DeckEntity(tagKey, title))
                }
            }
            if (keys.none { it in cardTagKeys }) continue
            val range = descendantsByBlock[b.uuid] ?: IntRange.EMPTY
            val answer = range.map { blocks[it] }
            val page = pageMap.getValue(b.pageUuid)
            val key = identity.keyFor(CardIdentity.pageKey(page.journalDay, page.name), b.content)
            cards += CardEntity(
                cardKey = key,
                blockUuid = b.uuid,
                pageUuid = b.pageUuid,
                answerHash = CardIdentity.answerHash(answer.map { (it.depth - b.depth) to it.content }),
                answerBlockCount = answer.count { it.content.isNotBlank() },
                sortIndex = cards.size,
            )
            keys.filter { it !in cardTagKeys }.forEach { cardCategories += CardCategoryEntity(key, it) }
        }

        val blockCount = blocks.size
        if (dto.pageCount != null && dto.pageCount != updatedPages.size) {
            warnings += "Export declares ${dto.pageCount} pages but contains ${updatedPages.size}"
        }
        if (dto.blockCount != null && dto.blockCount != blockCount) {
            warnings += "Export declares ${dto.blockCount} blocks but contains $blockCount"
        }

        return ImportBundle(
            graph = graph,
            schemaVersion = version,
            source = dto.source,
            logseqSchema = dto.logseqSchema?.let(::describeSchema),
            declaredPageCount = dto.pageCount,
            declaredBlockCount = dto.blockCount,
            excludedSystemViewBlocks = dto.excludedSystemViewBlocks,
            pages = updatedPages,
            blocks = blocks,
            tags = tags.values.map {
                TagEntity(it.key, it.name, it.name.lowercase(), it.pageUuid, it.blocks, it.pages.size)
            },
            blockTags = blockTags,
            pageTags = pageTags,
            refs = refs,
            cards = cards,
            cardCategories = cardCategories,
            decks = decks.values.toList(),
            hasTimestamps = hasTimestamps,
            warnings = warnings,
        )
    }

    private class TagAcc(val key: String, var name: String, val pageUuid: String?) {
        var blocks = 0
        val pages = LinkedHashSet<String>()
    }

    private fun invalid(reason: String): Nothing = throw ImportException(ImportError.Invalid(reason))

    /** Keeps array order unless every sibling carries an order key (then sorts by it, stably). */
    private fun sortedChildren(list: List<BlockDto>): List<BlockDto> =
        if (list.size > 1 && list.all { it.order != null }) list.sortedBy { it.order } else list

    private fun deckTarget(propertiesJson: String): String? {
        if (!propertiesJson.contains("query")) return null
        val obj = runCatching {
            ExportParser.json.parseToJsonElement(propertiesJson) as JsonObject
        }.getOrNull() ?: return null
        val q = obj.entries.firstOrNull { it.key.endsWith("/query") || it.key == "query" }?.value as? JsonPrimitive
        val text = q?.contentOrNull ?: return null
        val m = deckQueryRe.find(text) ?: return null
        return (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim().ifEmpty { null }
    }

    private fun describeSchema(e: JsonElement): String? = when (e) {
        is JsonObject -> {
            val major = (e[":major"] ?: e["major"]) as? JsonPrimitive
            val minor = (e[":minor"] ?: e["minor"]) as? JsonPrimitive
            if (major != null) "${major.content}.${minor?.content ?: "0"}" else e.toString()
        }
        is JsonPrimitive -> e.content
        else -> e.toString()
    }

    fun parseJournalDay(e: JsonElement?): Int? {
        val p = e as? JsonPrimitive ?: return null
        val raw = p.contentOrNull?.trim() ?: return null
        val digits = raw.replace("-", "")
        val n = digits.toIntOrNull() ?: return null
        return if (dayToDate(n) != null) n else null
    }

    private fun parseIsoDay(title: String): Int? = runCatching {
        val d = LocalDate.parse(title.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    }.getOrNull()

    fun dayToDate(day: Int): LocalDate? = runCatching {
        LocalDate.of(day / 10000, (day / 100) % 100, day % 100)
    }.getOrNull()?.takeIf { day in 10000101..99991231 }

    /** Unix milliseconds (numbers below 1e11 are treated as seconds) or ISO-8601 strings. */
    fun parseTimestamp(e: JsonElement?): Long? {
        val p = e as? JsonPrimitive ?: return null
        p.longOrNull?.let { return if (it in 1..99_999_999_999) it * 1000 else it.takeIf { v -> v > 0 } }
        val s = p.contentOrNull?.trim()?.ifEmpty { null } ?: return null
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
    }
}
