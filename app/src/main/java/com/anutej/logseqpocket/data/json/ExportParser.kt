package com.anutej.logseqpocket.data.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

data class ParsedExport(val dto: ExportDto, val sha256: String, val byteCount: Long)

/** Streams and decodes an export while hashing it, so unchanged files can be detected cheaply. */
class ExportParser(private val maxBytes: Long = DEFAULT_MAX_BYTES) {

    @OptIn(ExperimentalSerializationApi::class)
    fun parse(input: InputStream): ParsedExport {
        val digest = MessageDigest.getInstance("SHA-256")
        val counting = LimitedInputStream(input, maxBytes)
        val dto = try {
            DigestInputStream(counting, digest).buffered().use { json.decodeFromStream(ExportDto.serializer(), it) }
        } catch (e: LimitExceeded) {
            throw ImportException(ImportError.TooLarge(e.bytes, maxBytes))
        } catch (e: SerializationException) {
            throw ImportException(ImportError.MalformedJson(describe(e)))
        } catch (e: IllegalArgumentException) {
            throw ImportException(ImportError.MalformedJson(describe(e)))
        } catch (e: StackOverflowError) {
            throw ImportException(ImportError.Invalid("block nesting is too deep"))
        } catch (e: IOException) {
            throw ImportException(ImportError.Unreadable(e.message ?: e.javaClass.simpleName))
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return ParsedExport(dto, hex, counting.count)
    }

    /** A short reason that never echoes file content (parser messages can quote the input). */
    private fun describe(e: Exception): String {
        val msg = e.message.orEmpty()
        val offset = Regex("offset (\\d+)").find(msg)?.groupValues?.get(1)
        return when {
            msg.contains("too nested") -> "nesting is too deep"
            msg.contains("EOF", ignoreCase = true) || msg.contains("end of the input", ignoreCase = true) -> "file ends unexpectedly"
            offset != null -> "syntax error near byte $offset"
            else -> "unexpected structure"
        }
    }

    private class LimitExceeded(val bytes: Long) : IOException("limit exceeded")

    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) bump(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) bump(n.toLong())
            return n
        }

        private fun bump(n: Long) {
            count += n
            if (count > limit) throw LimitExceeded(count)
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 100L * 1024 * 1024

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            isLenient = false
        }
    }
}
