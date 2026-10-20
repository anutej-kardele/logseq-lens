package com.anutej.logseqpocket.data.repo

import com.anutej.logseqpocket.data.db.PageSummary
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.db.SearchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResults(
    val query: String,
    val pages: List<PageSummary>,
    val blocks: List<SearchRow>,
    /** Lowercase terms to highlight in results. */
    val terms: List<String>,
)

/**
 * Offline search over the local database. Plain word queries use the FTS4 index with prefix
 * matching; queries containing symbols (`dp[i]`, `<<`, `algo-pattern`) use an exact substring scan
 * so code fragments can be found too. User input is never passed to MATCH unsanitised.
 */
class SearchRepository(private val db: PocketDatabase) {
    private val wordRe = Regex("[\\p{L}\\p{N}]+")

    suspend fun search(raw: String, limit: Int = 200): SearchResults = withContext(Dispatchers.IO) {
        val q = raw.trim()
        if (q.isEmpty()) return@withContext SearchResults(raw, emptyList(), emptyList(), emptyList())
        val lower = q.lowercase()
        val words = wordRe.findAll(lower).map { it.value }.toList()
        val symbolic = q.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        val needle = escapeLike(lower)
        val dao = db.searchDao()
        val blocks = if (!symbolic && words.isNotEmpty()) {
            dao.searchBlocksFts(words.joinToString(" ") { "$it*" }, limit)
        } else {
            dao.searchBlocksLike(needle, limit)
        }
        val pages = dao.searchPages(needle, 30)
        SearchResults(raw, pages, blocks, if (symbolic || words.isEmpty()) listOf(lower) else words)
    }

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
