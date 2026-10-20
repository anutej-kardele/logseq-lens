package com.anutej.logseqpocket.data.scratchpad

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object PadDocSerializer : Serializer<PadDoc> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val defaultValue: PadDoc = PadDoc()

    override suspend fun readFrom(input: InputStream): PadDoc = try {
        json.decodeFromString(PadDoc.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Scratchpad file is unreadable", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Scratchpad file is unreadable", e)
    }

    override suspend fun writeTo(t: PadDoc, output: OutputStream) {
        output.write(json.encodeToString(PadDoc.serializer(), t).encodeToByteArray())
    }
}

// An unreadable file (should never happen; writes are atomic) starts a fresh note instead of crashing.
private val Context.scratchpadStore: DataStore<PadDoc> by dataStore(
    fileName = "scratchpad.json",
    serializer = PadDocSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { PadDoc() },
)

/**
 * Local-only storage for the single Scratchpad note. It lives in its own file, separate from the
 * imported Logseq database, so imports and "Clear local notes" never touch it. Writes are atomic and
 * serialised by DataStore, so a widget tick and an in-app edit can't overwrite each other.
 */
class ScratchpadStore(
    private val store: DataStore<PadDoc>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onChanged: () -> Unit = {},
) {
    constructor(context: Context, onChanged: () -> Unit) :
        this(context.applicationContext.scratchpadStore, onChanged = onChanged)

    val doc: Flow<PadDoc> = store.data

    suspend fun current(): PadDoc = store.data.first()

    suspend fun update(transform: (PadDoc) -> PadDoc): PadDoc {
        val result = store.updateData { old ->
            val next = transform(old)
            if (next == old) old else next.copy(updatedAt = clock())
        }
        onChanged()
        return result
    }
}
