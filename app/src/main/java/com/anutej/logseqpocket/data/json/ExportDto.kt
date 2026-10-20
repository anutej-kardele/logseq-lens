package com.anutej.logseqpocket.data.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wire model of the mobile JSON written by `logseq-backup/scripts/edn_to_json.py`.
 *
 * Every field is optional at the decoding layer so that missing or future fields never crash
 * parsing; [ExportValidator] decides what is actually required. Fields whose type has varied
 * between exporter variants (timestamps, journalDay) are kept as [JsonElement] and interpreted
 * leniently by the mapper.
 */
@Serializable
data class ExportDto(
    val schemaVersion: Int? = null,
    val source: String? = null,
    val graph: String? = null,
    val logseqSchema: JsonElement? = null,
    val pageCount: Int? = null,
    val blockCount: Int? = null,
    val excludedSystemViewBlocks: Int? = null,
    val pages: List<PageDto>? = null,
)

@Serializable
data class PageDto(
    val entityId: Long? = null,
    val uuid: String? = null,
    val name: String? = null,
    val title: String? = null,
    val kind: String? = null,
    val journalDay: JsonElement? = null,
    val createdAt: JsonElement? = null,
    val updatedAt: JsonElement? = null,
    val tags: List<@Serializable(EntityRefSerializer::class) EntityRefDto>? = null,
    val properties: JsonObject? = null,
    val references: List<@Serializable(EntityRefSerializer::class) EntityRefDto>? = null,
    val blocks: List<BlockDto>? = null,
)

@Serializable
data class BlockDto(
    val entityId: Long? = null,
    val uuid: String? = null,
    val content: String? = null,
    val order: String? = null,
    val collapsed: Boolean? = null,
    val createdAt: JsonElement? = null,
    val updatedAt: JsonElement? = null,
    val properties: JsonObject? = null,
    val tags: List<@Serializable(EntityRefSerializer::class) EntityRefDto>? = null,
    val references: List<@Serializable(EntityRefSerializer::class) EntityRefDto>? = null,
    val children: List<BlockDto>? = null,
)

/** A tag or reference. The exporter writes `{entityId, uuid, name}`; a bare string is accepted as a name. */
@Serializable
data class EntityRefDto(
    val entityId: Long? = null,
    val uuid: String? = null,
    val name: String? = null,
)

object EntityRefSerializer : JsonTransformingSerializer<EntityRefDto>(EntityRefDto.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && element.isString) buildJsonObject { put("name", element.content) } else element
}
