package com.anutej.logseqpocket.data.sync

import android.util.Log
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.importer.ImportSource
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.json.ImportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Read-only GitHub access. Every call is a GET:
 * - [sync] downloads one export file through the Contents API (`application/vnd.github.raw+json`) with
 *   `If-None-Match`, so an unchanged file costs a 304 and no rebuild. The body is streamed into
 *   [ImportService], which validates everything before replacing local data.
 * - [defaultBranch], [listBranches], [listJsonFiles] and [findJsonFiles] only read repository metadata to
 *   help fill in the Connect form and the graph switcher.
 * No write endpoints are called and no HTTP cache is configured (notes never land in a cache dir).
 */
class GitHubSync(
    private val db: PocketDatabase,
    private val importService: ImportService,
    private val tokens: TokenStore,
    private val apiBase: HttpUrl = API.toHttpUrl(),
    private val client: OkHttpClient = defaultClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(source: GitHubSourceConfig, force: Boolean = false): ImportOutcome = withContext(Dispatchers.IO) {
        source.validate()?.let { return@withContext ImportOutcome.Failed(ImportError.Invalid(it)) }
        val token = tokens.load()?.trim()
            ?: return@withContext ImportOutcome.Failed(ImportError.Auth("No GitHub token saved. Add a read-only token first."))
        val meta = db.notesDao().meta()
        // ETags are only meaningful for the same file; a different source always downloads in full.
        val sameSource = meta != null && meta.origin == ImportOrigin.GitHub.name && meta.originLabel == source.key
        val etag = meta?.etag?.takeIf { !force && sameSource }

        val url = apiBase.newBuilder()
            .addPathSegments("repos/${source.owner}/${source.repo}/contents/${source.path}")
            .addQueryParameter("ref", source.branch)
            .build()
        val request = get(url, token, raw = true).newBuilder()
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                when (resp.code) {
                    200 -> importService.import(
                        ImportSource(ImportOrigin.GitHub, source.key, resp.header("ETag")) { resp.body.byteStream() },
                        force = force || !sameSource,
                    )
                    304 -> {
                        db.importDao().touchMeta(clock(), etag)
                        ImportOutcome.Unchanged
                    }
                    else -> httpError(resp, source.repoSlug, notFound = "Export file not found at ${source.path} on ${source.branch}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.i(TAG, "Sync network failure: ${e.javaClass.simpleName}")
            ImportOutcome.Failed(ImportError.Network("Couldn't reach GitHub (offline?)."))
        }
    }

    /** The repository's default branch (e.g. "main" or "master"). */
    suspend fun defaultBranch(owner: String, repo: String, token: String? = null): Result<String> =
        getJson("repos/$owner/$repo", null, token, "$owner/$repo").mapCatching { json ->
            ((json as JsonObject)["default_branch"]?.jsonPrimitive?.contentOrNull) ?: error("no default branch")
        }

    suspend fun listBranches(owner: String, repo: String, token: String? = null): Result<List<String>> =
        getJson("repos/$owner/$repo/branches", mapOf("per_page" to "100"), token, "$owner/$repo").mapCatching { json ->
            (json as JsonArray).mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
        }

    /** Graph-export `.json` files directly inside [folder] on [branch] (full paths, sorted). */
    suspend fun listJsonFiles(
        owner: String, repo: String, branch: String, folder: String, token: String? = null,
    ): Result<List<String>> {
        val path = if (folder.isEmpty()) "repos/$owner/$repo/contents" else "repos/$owner/$repo/contents/$folder"
        return getJson(path, mapOf("ref" to branch), token, "$owner/$repo").mapCatching { json ->
            (json as JsonArray).mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val type = o["type"]?.jsonPrimitive?.contentOrNull
                val p = o["path"]?.jsonPrimitive?.contentOrNull
                p?.takeIf { type == "file" && isGraphExportCandidate(it) }
            }.sorted()
        }
    }

    /**
     * Every graph-export `.json` file on [branch] (one recursive tree listing), with `data/mobile/` first — used when only
     * a repo link was given.
     */
    suspend fun findJsonFiles(owner: String, repo: String, branch: String, token: String? = null): Result<List<String>> =
        getJson("repos/$owner/$repo/git/trees/$branch", mapOf("recursive" to "1"), token, "$owner/$repo").mapCatching { json ->
            val tree = (json as JsonObject)["tree"] as? JsonArray ?: JsonArray(emptyList())
            tree.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val p = o["path"]?.jsonPrimitive?.contentOrNull
                p?.takeIf { o["type"]?.jsonPrimitive?.contentOrNull == "blob" && isGraphExportCandidate(it) }
            }.sortedWith(compareBy({ !it.startsWith(PREFERRED_FOLDER) }, { it }))
        }

    private suspend fun getJson(
        path: String, query: Map<String, String>?, tokenOverride: String?, repoSlug: String,
    ): Result<JsonElement> = withContext(Dispatchers.IO) {
        val token = tokenOverride?.trim()?.ifEmpty { null } ?: tokens.load()?.trim()
            ?: return@withContext Result.failure(ImportException(ImportError.Auth("Add a read-only GitHub token first.")))
        val url = apiBase.newBuilder().addPathSegments(path).apply {
            query?.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        try {
            client.newCall(get(url, token, raw = false)).execute().use { resp ->
                if (resp.code != 200) {
                    val err = httpError(resp, repoSlug, notFound = "Not found: $repoSlug (or the token can't see it)")
                    return@withContext Result.failure(ImportException(err.error))
                }
                Result.success(JSON.parseToJsonElement(resp.body.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(ImportException(ImportError.Network("Couldn't reach GitHub (offline?).")))
        } catch (e: IllegalArgumentException) {
            Result.failure(ImportException(ImportError.Network("GitHub sent an unexpected response.")))
        }
    }

    private fun get(url: HttpUrl, token: String, raw: Boolean): Request = Request.Builder()
        .url(url)
        .get()
        .header("Authorization", "Bearer $token")
        .header("Accept", if (raw) "application/vnd.github.raw+json" else "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "LogseqPocket-Android")
        .build()

    private fun httpError(resp: Response, repoSlug: String, notFound: String): ImportOutcome.Failed = ImportOutcome.Failed(
        when (resp.code) {
            401 -> ImportError.Auth("GitHub rejected the token (expired or revoked). Replace it in Settings.")
            403, 429 ->
                if (resp.header("X-RateLimit-Remaining") == "0" || resp.code == 429) {
                    ImportError.Network("GitHub rate limit reached. Try again later.")
                } else {
                    ImportError.Auth("The token can't read $repoSlug. It needs Contents: Read-only on that repository.")
                }
            404 -> ImportError.Auth("$notFound.")
            else -> ImportError.Network("GitHub returned HTTP ${resp.code}.")
        }
    )

    companion object {
        private const val TAG = "GitHubSync"
        const val API = "https://api.github.com/"
        const val PREFERRED_FOLDER = "data/mobile/"
        private val JSON = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
