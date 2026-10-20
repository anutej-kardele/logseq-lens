package com.anutej.logseqpocket.domain

import com.anutej.logseqpocket.data.db.PageIndexRow
import com.anutej.logseqpocket.data.db.TagEntity
import com.anutej.logseqpocket.domain.markdown.isUuid

/** Where a reference in note text points. */
sealed interface RefTarget {
    val label: String
    data class Page(val uuid: String, override val label: String, val isTagPage: Boolean) : RefTarget
    data class Tag(val tagKey: String, override val label: String) : RefTarget
    /** Referenced page isn't in the export (or was excluded); shown muted, never invented. */
    data class Missing(override val label: String) : RefTarget
}

/** In-memory lookup of page titles and tags, rebuilt once per imported dataset. */
class RefIndex(pages: List<PageIndexRow>, tags: List<TagEntity>) {
    private val pagesByUuid = pages.associateBy { it.uuid.lowercase() }
    private val pagesByName = HashMap<String, PageIndexRow>().apply { pages.forEach { putIfAbsent(it.title.lowercase(), it) } }
    private val tagsByKey = tags.associateBy { it.tagKey }
    private val tagsByName = HashMap<String, TagEntity>().apply { tags.forEach { putIfAbsent(it.nameLower, it) } }

    /** `[[target]]` */
    fun page(target: String): RefTarget {
        val t = target.trim()
        if (isUuid(t)) {
            val key = t.lowercase()
            pagesByUuid[key]?.let { return asPageOrTag(it) }
            tagsByKey[key]?.let { return RefTarget.Tag(it.tagKey, it.name) }
            return RefTarget.Missing("Unknown page")
        }
        pagesByName[t.lowercase()]?.let { return asPageOrTag(it) }
        tagsByName[t.lowercase()]?.let { return RefTarget.Tag(it.tagKey, it.name) }
        return RefTarget.Missing(t)
    }

    /** `#tag` / `#[[target]]` — prefer the tag view. */
    fun tag(target: String): RefTarget {
        val t = target.trim()
        val tag = if (isUuid(t)) tagsByKey[t.lowercase()] else tagsByName[t.lowercase()]
        if (tag != null) return RefTarget.Tag(tag.tagKey, tag.name)
        return when (val p = page(t)) {
            is RefTarget.Missing -> RefTarget.Missing(if (isUuid(t)) "unknown tag" else t)
            else -> p
        }
    }

    fun tagName(tagKey: String): String? = tagsByKey[tagKey]?.name

    private fun asPageOrTag(p: PageIndexRow): RefTarget {
        val isTag = p.kind == "tag" || p.kind == "class"
        return if (isTag && tagsByKey.containsKey(p.uuid)) RefTarget.Tag(p.uuid, p.title)
        else RefTarget.Page(p.uuid, p.title, isTag)
    }

    companion object {
        val EMPTY = RefIndex(emptyList(), emptyList())
    }
}
