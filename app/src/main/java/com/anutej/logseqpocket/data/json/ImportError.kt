package com.anutej.logseqpocket.data.json

/**
 * Reasons an import can fail. Messages are shown to the user and deliberately never include
 * note content.
 */
sealed class ImportError(val userMessage: String) {
    class Unreadable(detail: String) : ImportError("Could not read the file: $detail")
    class TooLarge(bytes: Long, limit: Long) :
        ImportError("File is ${bytes / (1024 * 1024)} MB; the limit is ${limit / (1024 * 1024)} MB.")
    class MalformedJson(detail: String) : ImportError("The file is not valid Logseq Pocket JSON ($detail).")
    class UnsupportedSchema(val found: Int, val supported: Set<Int>) : ImportError(
        if (found > supported.max()) "This export uses schemaVersion $found, which is newer than this app supports " +
            "(${supported.joinToString()}). Update Logseq Pocket."
        else "schemaVersion $found is not supported (expected ${supported.joinToString()})."
    )
    class Invalid(reason: String) : ImportError("The export failed validation: $reason")
    class Network(detail: String) : ImportError(detail)
    class Auth(detail: String) : ImportError(detail)
    class Database(detail: String) : ImportError("Saving to the local database failed: $detail")
}

class ImportException(val error: ImportError) : Exception(error.userMessage)
