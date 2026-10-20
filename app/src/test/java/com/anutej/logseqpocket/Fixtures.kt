package com.anutej.logseqpocket

import com.anutej.logseqpocket.data.json.ExportDto
import com.anutej.logseqpocket.data.json.ExportParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream

/** Synthetic test data only — never real notes. */
object Fixtures {
    const val LEET = "10000000-0000-4000-8000-000000000001"
    const val ALGO = "10000000-0000-4000-8000-000000000002"
    const val HABIT = "10000000-0000-4000-8000-000000000003"
    const val J1 = "20000000-0000-4000-8000-000000000105"
    const val J2 = "20000000-0000-4000-8000-000000000106"
    const val J3 = "20000000-0000-4000-8000-000000000107"
    const val J4 = "20000000-0000-4000-8000-000000000108"
    const val ALG_NOTES = "30000000-0000-4000-8000-000000000001"
    fun block(n: Int) = "40000000-0000-4000-8000-%012x".format(n)

    val synthetic: String by lazy {
        Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/synthetic-activity.json")!!
            .readBytes().decodeToString()
    }

    fun stream(text: String) = ByteArrayInputStream(text.toByteArray())
    fun dto(text: String = synthetic): ExportDto = ExportParser().parse(stream(text)).dto

    fun root(text: String = synthetic): JsonObject = Json.parseToJsonElement(text).jsonObject

    /** Returns [text] with the top-level object transformed. */
    fun edit(text: String = synthetic, f: (MutableMap<String, JsonElement>) -> Unit): String {
        val m = root(text).toMutableMap()
        f(m)
        return JsonObject(m).toString()
    }

    /** Transforms the page with [pageUuid]. */
    fun editPage(text: String = synthetic, pageUuid: String, f: (MutableMap<String, JsonElement>) -> Unit): String =
        edit(text) { root ->
            root["pages"] = JsonArray(root.getValue("pages").jsonArray.map { p ->
                val pm = p.jsonObject.toMutableMap()
                if (pm["uuid"].toString().trim('"') == pageUuid) f(pm)
                JsonObject(pm)
            })
        }
}
