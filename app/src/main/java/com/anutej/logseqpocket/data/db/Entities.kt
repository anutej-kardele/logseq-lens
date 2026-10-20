package com.anutej.logseqpocket.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

// ---- Content tables: replaced wholesale on every successful import. ----

@Entity(tableName = "dataset_meta")
data class DatasetMetaEntity(
    @PrimaryKey val id: Int = 0,
    val graph: String,
    val schemaVersion: Int,
    val source: String?,
    val logseqSchema: String?,
    val pageCount: Int,
    val blockCount: Int,
    val declaredPageCount: Int?,
    val declaredBlockCount: Int?,
    val excludedSystemViewBlocks: Int?,
    val importedAt: Long,
    val origin: String,
    val originLabel: String?,
    val contentSha256: String,
    val byteCount: Long,
    val etag: String?,
    val warnings: String,
    val hasTimestamps: Boolean,
)

@Entity(
    tableName = "pages",
    indices = [Index("nameLower"), Index("kind", "journalDay"), Index("titleLower")],
)
data class PageEntity(
    @PrimaryKey val uuid: String,
    val entityId: Long?,
    val name: String,
    val nameLower: String,
    val title: String,
    val titleLower: String,
    val kind: String,
    val journalDay: Int?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val propertiesJson: String,
    val blockCount: Int,
    val nonEmptyBlockCount: Int,
)

@Entity(
    tableName = "blocks",
    indices = [Index("pageUuid", "preorder"), Index("parentUuid")],
)
data class BlockEntity(
    @PrimaryKey val uuid: String,
    val pageUuid: String,
    val parentUuid: String?,
    val depth: Int,
    val siblingIndex: Int,
    /** Position in a depth-first walk of the page; sorting by it reproduces the outline. */
    val preorder: Int,
    val orderKey: String?,
    val content: String,
    val collapsed: Boolean,
    val childCount: Int,
    val propertiesJson: String,
    val createdAt: Long?,
    val updatedAt: Long?,
)

@Fts4(contentEntity = BlockEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "blocks_fts")
data class BlockFts(val content: String)

@Entity(tableName = "tags", indices = [Index("nameLower")])
data class TagEntity(
    /** The Logseq UUID of the tag/class, or `name:<lowercase>` when the export gave none. */
    @PrimaryKey val tagKey: String,
    val name: String,
    val nameLower: String,
    /** Set when the tag has its own page in the export (user classes do; built-ins like Card don't). */
    val pageUuid: String?,
    val blockCount: Int,
    val pageCount: Int,
)

@Entity(tableName = "block_tags", primaryKeys = ["blockUuid", "tagKey"], indices = [Index("tagKey")])
data class BlockTagEntity(val blockUuid: String, val tagKey: String, val position: Int)

@Entity(tableName = "page_tags", primaryKeys = ["pageUuid", "tagKey"], indices = [Index("tagKey")])
data class PageTagEntity(val pageUuid: String, val tagKey: String)

/** References parsed from block text (the CLI export leaves `references` empty). */
@Entity(tableName = "block_refs", indices = [Index("blockUuid"), Index("targetPageUuid")])
data class BlockRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockUuid: String,
    val kind: String,
    val target: String,
    val targetPageUuid: String?,
)

@Entity(tableName = "cards", indices = [Index("blockUuid")])
data class CardEntity(
    @PrimaryKey val cardKey: String,
    val blockUuid: String,
    val pageUuid: String,
    val answerHash: String,
    val answerBlockCount: Int,
    val sortIndex: Int,
)

@Entity(tableName = "card_categories", primaryKeys = ["cardKey", "tagKey"], indices = [Index("tagKey")])
data class CardCategoryEntity(val cardKey: String, val tagKey: String)

/** A Logseq `#Cards` deck block whose query selects a tag, used for friendly category labels. */
@Entity(tableName = "decks")
data class DeckEntity(@PrimaryKey val tagKey: String, val title: String)

// ---- Local-only tables: never cleared by an import. ----

@Entity(tableName = "review_progress")
data class ReviewProgressEntity(
    @PrimaryKey val cardKey: String,
    val reviewCount: Int,
    val knowCount: Int,
    val againCount: Int,
    val lastResult: String?,
    val lastReviewedAt: Long?,
    /** Identity hints from the most recent import, used to re-attach progress when a prompt is edited. */
    val lastBlockUuid: String?,
    val lastAnswerHash: String?,
)

@Entity(tableName = "review_sessions")
data class ReviewSessionEntity(
    @PrimaryKey val deckKey: String,
    val cardKeysJson: String,
    val position: Int,
    val resultsJson: String,
    val shuffled: Boolean,
    val againOnly: Boolean,
    val startedAt: Long,
    val updatedAt: Long,
)
