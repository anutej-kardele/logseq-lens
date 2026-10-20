package com.anutej.logseqpocket.data.sync

import android.util.Log
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.importer.ImportSource
import com.anutej.logseqpocket.data.json.ImportError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fixed, non-secret location of the export. Only this one file is ever requested. */
object GitHubSource {
    const val OWNER = "anutej-kardele"
    const val REPO = "logseq-backup"
    const val BRANCH = "master"
    const val PATH = "data/mobile/activity.json"
    const val API = "https://api.github.com/"
    val label: String get() = "GitHub $OWNER/$REPO@$BRANCH"
}

/**
 * Read-only sync: one authenticated GET of the export through the GitHub Contents API
 * (`application/vnd.github.raw+json`), with `If-None-Match` so an unchanged file costs a 304 and
 * no rebuild. The body is streamed into [ImportService], which validates everything before
 * replacing local data, so a failed or partial download never touches the last working notes.
 * No write endpoints are called and no HTTP cache is configured (notes never land in a cache dir).
 */
class GitHubSync(
    private val db: PocketDatabase,
    private val importService: ImportService,
    private val tokens: TokenStore,
    private val apiBase: HttpUrl = GitHubSource.API.toHttpUrl(),
    private val client: OkHttpClient = defaultClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(force: Boolean = false): ImportOutcome = withContext(Dispatchers.IO) {
        val token = tokens.load()?.trim()
            ?: return@withContext ImportOutcome.Failed(ImportError.Auth("No GitHub token saved. Add a read-only token first."))
        val meta = db.notesDao().meta()
        val etag = meta?.etag?.takeIf { !force && meta.origin == ImportOrigin.GitHub.name }

        val url = apiBase.newBuilder()
            .addPathSegments("repos/${GitHubSource.OWNER}/${GitHubSource.REPO}/contents/${GitHubSource.PATH}")
            .addQueryParameter("ref", GitHubSource.BRANCH)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.raw+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "LogseqPocket-Android")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        val newEtag = resp.header("ETag")
                        importService.import(
                            ImportSource(ImportOrigin.GitHub, GitHubSource.label, newEtag) { resp.body.byteStream() },
                            force = force,
                        )
                    }
                    304 -> {
                        db.importDao().touchMeta(clock(), etag)
                        ImportOutcome.Unchanged
                    }
                    401 -> ImportOutcome.Failed(ImportError.Auth("GitHub rejected the token (expired or revoked). Replace it in Settings."))
                    403, 429 -> ImportOutcome.Failed(
                        if (resp.header("X-RateLimit-Remaining") == "0" || resp.code == 429) {
                            ImportError.Network("GitHub rate limit reached. Try again later.")
                        } else {
                            ImportError.Auth("The token can't read this repository. It needs Contents: Read-only on ${GitHubSource.REPO}.")
                        }
                    )
                    404 -> ImportOutcome.Failed(
                        ImportError.Auth("Export not found, or the token has no access to ${GitHubSource.OWNER}/${GitHubSource.REPO}.")
                    )
                    else -> ImportOutcome.Failed(ImportError.Network("GitHub returned HTTP ${resp.code}."))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.i(TAG, "Sync network failure: ${e.javaClass.simpleName}")
            ImportOutcome.Failed(ImportError.Network("Couldn't reach GitHub (offline?)."))
        }
    }

    companion object {
        private const val TAG = "GitHubSync"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
