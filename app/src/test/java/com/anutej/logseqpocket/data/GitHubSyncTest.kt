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
import com.anutej.logseqpocket.data.sync.GitHubSource
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
        val r = sync().sync()
        assertTrue("got $r", r is ImportOutcome.Imported)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals(
            "/repos/${GitHubSource.OWNER}/${GitHubSource.REPO}/contents/${GitHubSource.PATH}",
            req.url.encodedPath,
        )
        assertEquals(GitHubSource.BRANCH, req.url.queryParameter("ref"))
        assertEquals("Bearer $token", req.headers["Authorization"])
        assertEquals("application/vnd.github.raw+json", req.headers["Accept"])
        assertNull(req.headers["If-None-Match"])
        val meta = db.notesDao().meta()!!
        assertEquals(ImportOrigin.GitHub.name, meta.origin)
        assertEquals("\"v1\"", meta.etag)
    }

    @Test fun unchangedRemoteUsesEtagAndSkipsRebuild() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync()
        server.takeRequest()
        server.enqueue(status(304))
        assertEquals(ImportOutcome.Unchanged, sync().sync())
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
        assertEquals(8, db.notesDao().pageCount())
    }

    @Test fun changedRemoteReplacesData() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync()
        server.enqueue(ok(Fixtures.synthetic.replace("\"graph\": \"Synthetic\"", "\"graph\": \"Updated\""), etag = "\"v2\""))
        assertTrue(sync().sync() is ImportOutcome.Imported)
        assertEquals("Updated", db.notesDao().meta()!!.graph)
        assertEquals("\"v2\"", db.notesDao().meta()!!.etag)
    }

    @Test fun authAndAccessErrorsKeepLocalNotes() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync()
        for ((resp, expected) in listOf(
            status(401) to ImportError.Auth::class,
            status(403) to ImportError.Auth::class,
            status(404) to ImportError.Auth::class,
            status(403, "X-RateLimit-Remaining" to "0") to ImportError.Network::class,
            status(500) to ImportError.Network::class,
        )) {
            server.enqueue(resp)
            val r = sync().sync() as ImportOutcome.Failed
            assertTrue("got ${r.error}", expected.isInstance(r.error))
            assertEquals(8, db.notesDao().pageCount())
            assertEquals("Synthetic", db.notesDao().meta()!!.graph)
        }
    }

    @Test fun malformedOrTruncatedDownloadKeepsLocalNotes() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync()
        server.enqueue(ok(Fixtures.synthetic.take(500), etag = "\"broken\""))
        val r = sync().sync() as ImportOutcome.Failed
        assertTrue(r.error is ImportError.MalformedJson)
        assertEquals("\"v1\"", db.notesDao().meta()!!.etag)
        assertEquals(30, db.notesDao().blockCount())
    }

    @Test fun networkFailureKeepsLocalNotes() = runTest {
        server.enqueue(ok(Fixtures.synthetic))
        sync().sync()
        server.close()
        val r = sync().sync() as ImportOutcome.Failed
        assertTrue(r.error is ImportError.Network)
        assertEquals(8, db.notesDao().pageCount())
    }

    @Test fun missingTokenNeverCallsTheNetwork() = runTest {
        val r = sync(FakeTokens(null)).sync() as ImportOutcome.Failed
        assertTrue(r.error is ImportError.Auth)
        assertEquals(0, server.requestCount)
    }
}
