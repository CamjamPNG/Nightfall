// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.Library
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Library tab as navigation rather than one long register: a category narrows the catalogue to
 * one section, a search narrows it to the names that match, the empty state offers a way back, both
 * filters survive leaving the tab, and a section closes and opens again.
 *
 * Adding a row to the mix is measured in [LibraryAddTest], in its own JVM: it is the only scenario
 * here that writes the runtime's persisted state, and this repository runs one JVM per class so
 * scenarios cannot share it.
 *
 * Every scenario drives the shipped screen through the real activity — the chips, the field, the
 * section headers and the rows are the app's own composition — and every expected number is read
 * from `Library` instead of being transcribed into an assertion.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class LibraryNavigationTest : PlaybackHarness() {

    @Test
    fun aCategoryChipNarrowsTheCatalogueToOneSection() {
        openLibrary()
        compose.onNode(isSelectable() and hasText("Rain")).performClick()
        compose.waitForIdle()

        val rain = Library.all.count { it.category == "Rain" }
        assertTrue("the Rain section did not show its own count", onScreen("$rain available"))
        assertTrue("the first rain entry is missing", onScreen("Light drizzle"))
        // The catalogue's first row is Noise, and filtering must take it away rather than leave it
        // four screens up: once Rain is the only section, no noise row is composed at all.
        assertFalse("a noise entry survived the Rain filter", onScreen("White"))
    }

    @Test
    fun typingInSearchNarrowsTheCatalogue() {
        openLibrary()
        compose.onNodeWithContentDescription("Search sounds").performTextInput("thunder")
        compose.waitForIdle()

        assertTrue("a matching entry is missing", onScreen("Rolling thunder"))
        assertFalse("a non-matching entry survived the search", onScreen("White"))
    }

    @Test
    fun theEmptyStateAppearsAndItsActionClearsTheFilters() {
        openLibrary()
        compose.onNodeWithContentDescription("Search sounds").performTextInput("zzzq")
        compose.waitForIdle()
        assertTrue("the empty state did not appear", onScreen("No sounds match your search."))
        assertFalse("the empty state listed a sound anyway", onScreen("White"))

        compose.onNodeWithText("Show all sounds").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Search sounds")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertTrue("clearing the filters did not bring the catalogue back", onScreen("White"))
        assertFalse("the empty state survived its own action", onScreen("No sounds match your search."))
    }

    @Test
    fun theFiltersSurviveLeavingAndReturningToTheTab() {
        openLibrary()
        compose.onNode(isSelectable() and hasText("Noise")).performClick()
        compose.onNodeWithContentDescription("Search sounds").performTextInput("white")
        compose.waitForIdle()
        assertTrue("the filtered entry is missing before the tab switch", onScreen("White"))

        compose.onNodeWithText("Presets", useUnmergedTree = true).performClick()
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText("Find your night", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.layoutInfo.isPlaced }
        }
        openLibrary()

        compose.onNode(isSelectable() and hasText("Noise")).assertIsSelected()
        compose.onNodeWithContentDescription("Search sounds").assertTextContains("white")
        assertTrue("the filtered entry is missing after the tab switch", onScreen("White"))
    }

    @Test
    fun aSectionClosesAndOpensAgain() {
        openLibrary()
        assertTrue("the first noise entry is missing before closing", onScreen("White"))

        sectionHeader("Noise", "Expanded").performClick()
        compose.waitForIdle()
        assertFalse("a row survived its section closing", onScreen("White"))
        sectionHeader("Noise", "Collapsed")

        sectionHeader("Noise", "Collapsed").performClick()
        compose.waitForIdle()
        assertTrue("the section did not open again", onScreen("White"))
        sectionHeader("Noise", "Expanded")
    }

    /** A section's own header, told apart from the chip that carries the same word. */
    private fun sectionHeader(name: String, state: String) =
        compose.onNode(hasText(name) and hasStateDescription(state))

    /** Opens the tab the way a person does and waits for the page's own heading to be placed. */
    private fun openLibrary() {
        compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText("Sound library", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.layoutInfo.isPlaced }
        }
        compose.waitForIdle()
    }

    private fun onScreen(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .any { it.layoutInfo.isPlaced }
}
