package com.anutej.logseqpocket

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anutej.logseqpocket.data.importer.ImportOrigin
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.importer.ImportSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end smoke test on a device/emulator using the synthetic fixture only. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun importFixture() = runBlocking {
        val instr = InstrumentationRegistry.getInstrumentation()
        val app = instr.targetContext.applicationContext as LogseqPocketApp
        val r = app.container.importService.import(
            ImportSource(ImportOrigin.File, "synthetic-activity.json") { instr.context.assets.open("fixtures/synthetic-activity.json") },
            force = true,
        )
        assertTrue("import failed: $r", r is ImportOutcome.Imported)
    }

    @Test fun browseJournalsReaderCardsAndSearch() {
        compose.waitUntilAtLeastOneExists(hasText("Synthetic"), 20_000)

        compose.onAllNodesWithText("Journals").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Monday, 5 January 2026"), 10_000)
        compose.onNodeWithText("Monday, 5 January 2026").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Sliding Window"), 10_000)
        compose.onNodeWithText("Algorithms Notes", substring = true).assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onAllNodesWithText("Cards").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("LeetCode Deck"), 10_000)
        compose.onNodeWithText("LeetCode Deck").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Show answer"), 10_000)
        compose.onNodeWithText("Show answer").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Know"), 10_000)
        compose.onNodeWithText("Know").performClick()
        compose.waitUntilAtLeastOneExists(hasText("2 / 3"), 10_000)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onAllNodesWithText("Search").onFirst().performClick()
        compose.onNodeWithText("Search titles and notes").performTextInput("complements")
        compose.waitUntilAtLeastOneExists(hasText("BLOCKS · 1"), 10_000)
    }
}
