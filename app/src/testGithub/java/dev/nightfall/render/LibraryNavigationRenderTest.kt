// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Library as navigation, in five frames plus the one measurement a still frame cannot carry.
 *
 * A picture cannot say whether the search field is pinned or merely happened to be near the top when
 * the shot was taken, so the frames are drawn after the list has moved and the field's own position
 * is compared against where it stood at rest: pinning is an equality between two measurements, not a
 * look.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class LibraryNavigationRenderTest : ScreenshotHarness() {

    @Test
    fun theLibraryIsNavigable() {
        open("Library", "Sound library")
        val searchAtRest = searchTop()
        assertTrue("the search field is not at the top of the screen", searchAtRest < 60 * 2.625f)
        capture("10-library-sections.png")

        scrollScreenTo(12)
        assertEquals(
            "the search field moved with the list instead of staying pinned",
            searchAtRest,
            searchTop(),
            0.5f,
        )
        assertTrue("the chips left the screen with the list", onScreen("Rain"))
        capture("11-library-scrolled.png")

        compose.onNode(isSelectable() and hasText("Rain")).performClick()
        compose.waitForIdle()
        val rain = Library.all.count { it.category == "Rain" }
        assertTrue("the Rain filter did not leave one section", onScreen("$rain available"))
        assertTrue("the first rain entry is missing", onScreen("Light drizzle"))
        assertTrue("a noise entry survived the Rain filter", !onScreen("White"))
        capture("12-library-one-category.png")

        compose.onNode(hasText("Rain") and hasStateDescription("Expanded")).performClick()
        compose.waitForIdle()
        assertFalse("the closed section kept a row", onScreen("Light drizzle"))
        assertTrue("the closed section kept no header", onScreen("Rain"))
        capture("13-library-section-closed.png")

        compose.onNodeWithContentDescription("Search sounds").performTextInput("zzzq")
        compose.waitForIdle()
        assertTrue("the empty state did not appear", onScreen("No sounds match your search."))
        capture("14-library-empty.png")

        // The longest section, entered at its far end: a sticky header has to survive twenty-three
        // rows underneath it, not just the four that fit a screen.
        compose.onNodeWithContentDescription("Search sounds").performTextClearance()
        compose.onNode(isSelectable() and hasText("All")).performClick()
        compose.waitForIdle()
        val longest = Library.all.groupBy { it.category }.maxBy { it.value.size }
        screenList().performScrollToKey(longest.value[longest.value.lastIndex - 2].id)
        compose.waitForIdle()
        val header = sectionHeader(longest.key)
        val listTop = screenList().fetchSemanticsNode().boundsInRoot.top
        assertEquals(
            "the ${longest.key} header is not pinned to the top of the list",
            listTop,
            header.top,
            60f,
        )
        assertTrue(
            "the ${longest.key} rows are not under their header",
            onScreen(longest.value.last().name),
        )
        capture("15-library-longest-section.png")
    }

    private fun sectionHeader(name: String) =
        compose.onNode(hasText(name) and hasStateDescription("Expanded")).fetchSemanticsNode().boundsInRoot

    /** The pinned search field's top edge in root pixels: the measurement pinning consists of. */
    private fun searchTop() =
        compose.onNodeWithContentDescription("Search sounds").fetchSemanticsNode().boundsInRoot.top
}
