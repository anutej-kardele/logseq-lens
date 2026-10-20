package com.anutej.logseqpocket.domain

import com.anutej.logseqpocket.data.json.ExportParser
import com.anutej.logseqpocket.ui.util.Format
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

data class PropChip(val label: String, val value: String, val kind: Kind) {
    enum class Kind { Plain, Query, Review }
}

/** Turns a block's exported properties into short, read-only labels. Raw values are never edited. */
object PropertyFormatter {
    private val uuidRef = Regex("""\[\[([0-9a-fA-F-]{36})]]""")

    fun format(propertiesJson: String, refs: RefIndex): List<PropChip> {
        if (propertiesJson.length <= 2) return emptyList()
        val obj = runCatching { ExportParser.json.parseToJsonElement(propertiesJson) as JsonObject }.getOrNull()
            ?: return emptyList()
        return obj.entries.mapNotNull { (key, value) ->
            when {
                key.endsWith("fsrs/due") -> (value as? JsonPrimitive)?.longOrNull?.let {
                    PropChip("Logseq review due", Format.timestamp(it), PropChip.Kind.Review)
                }
                key.endsWith("fsrs/state") -> fsrsState(value)?.let { PropChip("Logseq review", it, PropChip.Kind.Review) }
                key.endsWith("/query") || key == "query" -> (value as? JsonPrimitive)?.contentOrNull?.let { q ->
                    PropChip("Query", uuidRef.replace(q) { m -> "#" + refs.page(m.groupValues[1]).label }, PropChip.Kind.Query)
                }
                else -> PropChip(prettyKey(key), valueText(value, refs), PropChip.Kind.Plain)
            }
        }
    }

    private fun fsrsState(v: JsonElement): String? {
        val o = v as? JsonObject ?: return null
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.removePrefix(":")
        return listOfNotNull(
            s(":state"),
            s(":reps")?.let { "$it reps" },
            s(":logseq/last-rating")?.let { "last: $it" },
        ).joinToString(" · ").ifEmpty { null }
    }

    fun prettyKey(key: String): String =
        key.removePrefix(":").substringAfterLast('/').substringAfterLast('.').replace('-', ' ')

    private fun valueText(v: JsonElement, refs: RefIndex): String = when (v) {
        is JsonPrimitive -> v.contentOrNull?.let { c -> uuidRef.replace(c) { refs.page(it.groupValues[1]).label } } ?: "—"
        is JsonArray -> v.joinToString(", ") { valueText(it, refs) }
        is JsonObject -> v.toString().take(80)
    }.take(160)
}
