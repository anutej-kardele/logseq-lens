package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.data.sync.DEFAULT_SOURCE
import com.anutej.logseqpocket.data.sync.GitHubSourceConfig
import com.anutej.logseqpocket.data.sync.ParsedGitHubLink
import com.anutej.logseqpocket.data.sync.isGraphExportCandidate
import com.anutej.logseqpocket.data.sync.parseGitHubLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubSourceTest {
    private val repo = ParsedGitHubLink("anutej-kardele", "logseq-backup")

    @Test fun cloneLinksAndShorthand() {
        for (link in listOf(
            "https://github.com/anutej-kardele/logseq-backup.git",
            "https://github.com/anutej-kardele/logseq-backup",
            "github.com/anutej-kardele/logseq-backup/",
            "  https://www.github.com/anutej-kardele/logseq-backup.git  ",
            "git@github.com:anutej-kardele/logseq-backup.git",
            "anutej-kardele/logseq-backup",
            "https://github.com/anutej-kardele/logseq-backup/pulls",
        )) assertEquals(link, repo, parseGitHubLink(link))
    }

    @Test fun fileFolderAndRawLinks() {
        assertEquals(
            ParsedGitHubLink("o", "r", "master", "data/mobile/activity.json"),
            parseGitHubLink("https://github.com/o/r/blob/master/data/mobile/activity.json"),
        )
        assertEquals(
            ParsedGitHubLink("o", "r", "dev", "data/mobile", isFolder = true),
            parseGitHubLink("https://github.com/o/r/tree/dev/data/mobile"),
        )
        assertEquals(
            ParsedGitHubLink("o", "r", "main", null, isFolder = true),
            parseGitHubLink("https://github.com/o/r/tree/main"),
        )
        assertEquals(
            ParsedGitHubLink("o", "r", "master", "data/mobile/demo.json"),
            parseGitHubLink("https://raw.githubusercontent.com/o/r/master/data/mobile/demo.json?token=x"),
        )
    }

    @Test fun garbageIsRejected() {
        for (bad in listOf("", "hello", "https://gitlab.com/o/r", "https://github.com/onlyowner", "o/r/x/y", "o r/x"))
            assertNull(bad, parseGitHubLink(bad))
    }

    @Test fun validation() {
        val mine = GitHubSourceConfig("anutej-kardele", "logseq-backup", "master", "data/mobile/activity.json")
        assertNull(mine.validate())
        assertNotNull(mine.copy(owner = "bad owner").validate())
        assertNotNull(mine.copy(branch = "").validate())
        assertNotNull(mine.copy(path = "../secrets.json").validate())
        assertNotNull(mine.copy(path = "/data/a.json").validate())
        assertNotNull(mine.copy(path = "data/mobile/activity.edn").validate())
        assertEquals("data/mobile", mine.folder)
        assertEquals("activity.json", mine.fileName)
        // A fresh install has no repo: the Connect form starts empty.
        assertFalse(DEFAULT_SOURCE.isConfigured)
        assertNotNull(DEFAULT_SOURCE.validate())
        assertEquals("data/mobile/activity.json", DEFAULT_SOURCE.path)
        assertEquals("", GitHubSourceConfig("o", "r", "main", "a.json").folder)
    }

    @Test fun onlyGraphExportsAreOffered() {
        for (ok in listOf("data/mobile/activity.json", "data/mobile/demo.json", "exports/Work Notes.JSON", "a.json"))
            assertEquals(ok, true, isGraphExportCandidate(ok))
        for (no in listOf(
            "data/mobile/manifest.json", "config.example.json", "config.json", "data/config.local.json",
            "package.json", "sub/package-lock.json", "x.sample.json", "readme.md",
        )) assertEquals(no, false, isGraphExportCandidate(no))
    }

    @Test fun sourceKeyRoundTrips() {
        val src = GitHubSourceConfig("friend", "notes-backup", "main", "data/mobile/demo.json")
        assertEquals(src, com.anutej.logseqpocket.data.sync.sourceFromKey(src.key))
        assertNull(com.anutej.logseqpocket.data.sync.sourceFromKey("GitHub anutej-kardele/logseq-backup@master"))
        assertNull(com.anutej.logseqpocket.data.sync.sourceFromKey(null))
    }
}
