package com.anutej.logseqpocket.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anutej.logseqpocket.Fixtures
import com.anutej.logseqpocket.data.db.PocketDatabase
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportService
import com.anutej.logseqpocket.data.json.ImportError
import com.anutej.logseqpocket.data.sync.DEFAULT_SOURCE
import com.anutej.logseqpocket.data.sync.GitHubSourceConfig
import com.anutej.logseqpocket.data.importer.ImportCoordinator
import com.anutej.logseqpocket.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import com.anutej.logseqpocket.data.sync.GitHubSync
import com.anutej.logseqpocket.data.sync.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class FakeTokens(initial: String?) : TokenStore {
    private val value = MutableStateFlow(initial)
    override val hasToken = value.map { it != null }
    override suspend fun load() = value.value
    override suspend fun save(token: String) { value.value = token }
    override suspend fun clear() { value.value = null }
}

@RunWith(AndroidJUnit4::class)
class GitHubSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var db: PocketDatabase
    private val token = "github_pat_TEST_ONLY_0123456789"

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PocketDatabase::class.java).build()
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    private fun sync(tokens: TokenStore = FakeTokens(token)) =
        GitHubSync(db, ImportService(db), tokens, apiBase = server.url("/"))

    private fun ok(body: String, etag: String = "\"v1\"") =
        MockResponse.Builder().code(200).body(body).addHeader("ETag", etag).build()

    private fun status(code: Int, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(code).apply { headers.forEach { addHeader(it.first, it.second) } }.build()

    @Test fun downloadsOnlyTheExportWithReadOnlyAuthenticatedGet() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        val r = sync().sync(DEFAULT_SOURCE)
        assertTrue("got $r", r is ImportOutcome.Imported)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals(
            "/repos/${DEFAULT_SOURCE.owner}/${DEFAULT_SOURCE.repo}/contents/${DEFAULT_SOURCE.path}",
            req.url.encodedPath,
        )
        assertEquals(DEFAULT_SOURCE.branch, req.url.queryParameter("ref"))
        assertEquals("Bearer $token", req.headers["Authorization"])
        assertEquals("application/vnd.github.raw+json", req.headers["Accept"])
        assertNull(req.headers["If-None-Match"])
        val meta = db.notesDao().meta()!!
        assertEquals(ImportOrigin.GitHub.name, meta.origin)
        assertEquals("\"v1\"", meta.etag)
    }

    @Test fun unchangedRemoteUsesEtagAndSkipsRebuild() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync(DEFAULT_SOURCE)
        server.takeRequest()
        server.enqueue(status(304))
        assertEquals(ImportOutcome.Unchanged, sync().sync(DEFAULT_SOURCE))
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
        assertEquals(8, db.notesDao().pageCount())
    }

    @Test fun changedRemoteReplacesData() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync(DEFAULT_SOURCE)
        server.enqueue(ok(Fixtures.synthetic.replace("\"graph\": \"Synthetic\"", "\"graph\": \"Updated\""), etag = "\"v2\""))
        assertTrue(sync().sync(DEFAULT_SOURCE) is ImportOutcome.Imported)
        assertEquals("Updated", db.notesDao().meta()!!.graph)
        assertEquals("\"v2\"", db.notesDao().meta()!!.etag)
    }

    @Test fun authAndAccessErrorsKeepLocalNotes() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync(DEFAULT_SOURCE)
        for ((resp, expected) in listOf(
            status(401) to ImportError.Auth::class,
            status(403) to ImportError.Auth::class,
            status(404) to ImportError.Auth::class,
            status(403, "X-RateLimit-Remaining" to "0") to ImportError.Network::class,
            status(500) to ImportError.Network::class,
        )) {
            server.enqueue(resp)
            val r = sync().sync(DEFAULT_SOURCE) as ImportOutcome.Failed
            assertTrue("got ${r.error}", expected.isInstance(r.error))
            assertEquals(8, db.notesDao().pageCount())
            assertEquals("Synthetic", db.notesDao().meta()!!.graph)
        }
    }

    @Test fun malformedOrTruncatedDownloadKeepsLocalNotes() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync(DEFAULT_SOURCE)
        server.enqueue(ok(Fixtures.synthetic.take(500), etag = "\"broken\""))
        val r = sync().sync(DEFAULT_SOURCE) as ImportOutcome.Failed
        assertTrue(r.error is ImportError.MalformedJson)
        assertEquals("\"v1\"", db.notesDao().meta()!!.etag)
        assertEquals(30, db.notesDao().blockCount())
    }

    @Test fun networkFailureKeepsLocalNotes() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync(DEFAULT_SOURCE)
        server.close()
        val r = sync().sync(DEFAULT_SOURCE) as ImportOutcome.Failed
        assertTrue(r.error is ImportError.Network)
        assertEquals(8, db.notesDao().pageCount())
    }

    @Test fun missingTokenNeverCallsTheNetwork() = runTest {
        val r = sync(FakeTokens(null)).sync(DEFAULT_SOURCE) as ImportOutcome.Failed
        assertTrue(r.error is ImportError.Auth)
        assertEquals(0, server.requestCount)
    }

    private val demo = GitHubSourceConfig("friend", "notes-backup", "main", "exports/demo.json")

    private fun jsonResp(body: String) = MockResponse.Builder().code(200).body(body).build()

    @Test fun customSourceBuildsTheRightUrlAndDoesNotReuseEtags() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync(DEFAULT_SOURCE)
        server.takeRequest()
        server.enqueue(ok(Fixtures.synthetic.replace("\"graph\": \"Synthetic\"", "\"graph\": \"Demo\""), etag = "\"d1\""))
        assertTrue(sync().sync(demo) is ImportOutcome.Imported)
        val req = server.takeRequest()
        assertEquals("/repos/friend/notes-backup/contents/exports/demo.json", req.url.encodedPath)
        assertEquals("main", req.url.queryParameter("ref"))
        assertNull(req.headers["If-None-Match"]) // the stored ETag belonged to another file
        assertEquals("Demo", db.notesDao().meta()!!.graph)
        assertEquals(demo.key, db.notesDao().meta()!!.originLabel)
    }

    @Test fun invalidSourceNeverCallsTheNetwork() = runTest {
        val r = sync().sync(DEFAULT_SOURCE.copy(path = "../x.json")) as ImportOutcome.Failed
        assertTrue(r.error is ImportError.Invalid)
        assertEquals(0, server.requestCount)
    }

    @Test fun repoOnlyLinkDiscoversBranchAndExportFile() = runTest {
        server.enqueue(jsonResp("""{"default_branch":"main","name":"notes-backup"}"""))
        server.enqueue(jsonResp("""{"sha":"x","truncated":false,"tree":[
            {"path":"README.md","type":"blob"},
            {"path":"config.json","type":"blob"},
            {"path":"data","type":"tree"},
            {"path":"data/mobile/activity.json","type":"blob"},
            {"path":"data/mobile/demo.JSON","type":"blob"}]}"""))
        val s = sync()
        assertEquals("main", s.defaultBranch("friend", "notes-backup").getOrThrow())
        val files = s.findJsonFiles("friend", "notes-backup", "main").getOrThrow()
        assertEquals(listOf("data/mobile/activity.json", "data/mobile/demo.JSON", "config.json"), files)
        assertEquals("/repos/friend/notes-backup", server.takeRequest().url.encodedPath)
        val tree = server.takeRequest()
        assertEquals("/repos/friend/notes-backup/git/trees/main", tree.url.encodedPath)
        assertEquals("1", tree.url.queryParameter("recursive"))
    }

    @Test fun branchAndFolderListings() = runTest {
        server.enqueue(jsonResp("""[{"name":"main"},{"name":"dev"}]"""))
        server.enqueue(jsonResp("""[{"name":"activity.json","path":"data/mobile/activity.json","type":"file"},
            {"name":"old","path":"data/mobile/old","type":"dir"},
            {"name":"notes.txt","path":"data/mobile/notes.txt","type":"file"},
            {"name":"demo.json","path":"data/mobile/demo.json","type":"file"}]"""))
        val s = sync()
        assertEquals(listOf("main", "dev"), s.listBranches("o", "r").getOrThrow())
        assertEquals(listOf("data/mobile/activity.json", "data/mobile/demo.json"), s.listJsonFiles("o", "r", "main", "data/mobile").getOrThrow())
    }

    @Test fun listingErrorsAreReadable() = runTest {
        server.enqueue(status(404))
        val e = sync().listBranches("o", "missing").exceptionOrNull() as com.anutej.logseqpocket.data.json.ImportException
        assertTrue(e.error is ImportError.Auth)
        assertTrue(e.error.userMessage.contains("o/missing"))
    }

    @Test fun switchingSourceCommitsOnlyAfterSuccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(context)
        settings.setGitHubSource(DEFAULT_SOURCE)
        val coordinator = ImportCoordinator(context, CoroutineScope(Dispatchers.Unconfined), ImportService(db), sync(), settings)
        server.enqueue(ok(Fixtures.synthetic))
        server.enqueue(jsonResp("[]")) // file list refresh
        assertTrue(coordinator.syncGitHubNow() is ImportOutcome.Imported)

        // A failing switch keeps both the old graph and the old source.
        server.enqueue(status(404))
        assertTrue(coordinator.syncGitHubNow(demo) is ImportOutcome.Failed)
        assertEquals(DEFAULT_SOURCE, settings.current().githubSource)
        assertEquals("Synthetic", db.notesDao().meta()!!.graph)

        // A successful switch replaces the graph and makes the new source active.
        server.enqueue(ok(Fixtures.synthetic.replace("\"graph\": \"Synthetic\"", "\"graph\": \"Demo\""), etag = "\"d\""))
        server.enqueue(jsonResp("""[{"path":"exports/demo.json","type":"file"},{"path":"exports/other.json","type":"file"}]"""))
        assertTrue(coordinator.syncGitHubNow(demo) is ImportOutcome.Imported)
        assertEquals(demo, settings.current().githubSource)
        assertEquals("Demo", db.notesDao().meta()!!.graph)
        assertEquals(listOf("exports/demo.json", "exports/other.json"), settings.settings.first().githubFiles)
        settings.setGitHubSource(DEFAULT_SOURCE) // leave shared prefs clean for other tests
    }
}
