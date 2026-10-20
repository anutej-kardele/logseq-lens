package com.anutej.logseqpocket.data

import com.anutej.logseqpocket.data.sync.DEFAULT_SOURCE
import com.anutej.logseqpocket.data.sync.GitHubSourceConfig
import com.anutej.logseqpocket.data.sync.ParsedGitHubLink
import com.anutej.logseqpocket.data.sync.parseGitHubLink
import org.junit.Assert.assertEquals
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
        assertNull(DEFAULT_SOURCE.validate())
        assertNotNull(DEFAULT_SOURCE.copy(owner = "bad owner").validate())
        assertNotNull(DEFAULT_SOURCE.copy(branch = "").validate())
        assertNotNull(DEFAULT_SOURCE.copy(path = "../secrets.json").validate())
        assertNotNull(DEFAULT_SOURCE.copy(path = "/data/a.json").validate())
        assertNotNull(DEFAULT_SOURCE.copy(path = "data/mobile/activity.edn").validate())
        assertEquals("data/mobile", DEFAULT_SOURCE.folder)
        assertEquals("activity.json", DEFAULT_SOURCE.fileName)
        assertEquals("", GitHubSourceConfig("o", "r", "main", "a.json").folder)
    }
}
