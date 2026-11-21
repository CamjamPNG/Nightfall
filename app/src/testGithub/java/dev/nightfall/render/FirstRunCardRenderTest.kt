// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first-run card as Home draws it: dark and light, at a 1.0×, a 1.3× and a 2.0× font scale, on
 * the same 411×914 dp window every other frame in this listing uses.
 *
 * Three rows that each wrap to two lines is exactly the shape a fixed height clips, so this measures
 * as well as draws. The scale is the app's own configuration — `RuntimeEnvironment.setFontScale`,
 * the same update the system sends — and the measured growth of the card's heading is asserted, so a
 * frame that ignored the scale would fail here instead of passing as a picture of small text. At
 * 2.0× the card is taller than the visible Home area, so there is a second frame from the bottom of
 * the same card, after every row and the dismiss are checked to be displayed rather than cut.
 *
 * Measured on this harness: the heading is 63 px at 1.0×, 80 px at 1.3× and 111 px at 2.0×. That is
 * 1.27× and 1.76× rather than exactly 1.3× and 2.0×, which is what Compose's sp metrics give under
 * Robolectric; the assertions below hold the measured ratios, not the nominal ones.
 *
 * Not judged by eye: nobody has seen this on a device, and a still frame cannot show how it reads
 * while scrolling.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class FirstRunCardRenderTest : ScreenshotHarness() {

    @Test
    fun theCardDrawsInBothThemesAtEveryFontScale() {
        assertTrue("the card is not on Home", onScreen(TITLE))
        val base = titleHeight()
        capture("18-first-run-dark.png")

        atScale(1.3f)
        assertTrue("the card left the frame at 1.3×", onScreen(TITLE))
        assertTrue(
            "the heading measured ${titleHeight()}px against ${base}px at 1.0×",
            titleHeight() > base * 1.2f,
        )
        capture("19-first-run-dark-1.3x.png")

        atScale(2f)
        assertTrue("the card left the frame at 2.0×", onScreen(TITLE))
        assertTrue(
            "the heading measured ${titleHeight()}px against ${base}px at 1.0×",
            titleHeight() > base * 1.7f,
        )
        capture("20-first-run-dark-2.0x.png")
        everyPartCanBeReached()
        // Past the card's own bottom, so the second frame is the rows the first one could not hold.
        scrollTo(hasText(PHRASE_BOX))
        capture("21-first-run-dark-2.0x-card-bottom.png")

        compose.runOnIdle { runtime.change { it.copy(settings = it.settings.copy(light = true)) } }
        compose.waitForIdle()
        atScale(2f)
        assertTrue("the card left the light frame", onScreen(TITLE))
        scrollTo(hasText(TITLE))
        capture("22-first-run-light-2.0x.png")
        everyPartCanBeReached()
        scrollTo(hasText(PHRASE_BOX))
        capture("23-first-run-light-2.0x-card-bottom.png")

        atScale(1.3f)
        scrollTo(hasText(TITLE))
        assertTrue("the card left the light frame at 1.3×", onScreen(TITLE))
        capture("24-first-run-light-1.3x.png")

        // Back to the defaults this machine's later scenarios start from.
        atScale(1f)
        compose.runOnIdle { runtime.change { it.copy(settings = it.settings.copy(light = false)) } }
    }

    /** The system's own font-scale update, not a mock of one. */
    private fun atScale(scale: Float) {
        compose.runOnIdle { RuntimeEnvironment.setFontScale(scale) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** The card's own heading, in pixels: the one text that is on screen at every scale. */
    private fun titleHeight(): Float =
        compose.onAllNodesWithText(TITLE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot
            .height

    /**
     * Every part of the card can be scrolled into view and is drawn there, which is what a fixed
     * height would break: at 2.0× the last row and the dismiss are below the fold, not missing.
     */
    private fun everyPartCanBeReached() {
        for (text in ROWS + "Not now") reach(text)
    }

    /**
     * Scrolls Home down in steps until [text] is on screen, then asserts it is drawn there. Steps
     * rather than `performScrollToNode`, because the card is taller than the window at a 2.0× scale
     * and a lazy list can only scroll an item, not a point inside one.
     */
    private fun reach(text: String) {
        fun nodes() = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("there is no single $text", nodes().size == 1)
        var steps = 0
        while (nodes().first().boundsInRoot.height <= 0f && steps < 12) {
            homeList().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
            compose.waitForIdle()
            steps++
        }
        compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    /** Home's own list, the tallest scrollable in the tree. */
    private fun homeList() =
        compose.onAllNodes(hasScrollAction()).let { all ->
            val nodes = all.fetchSemanticsNodes()
            val tallest = nodes.indices.maxBy { nodes[it].boundsInRoot.height }
            all[tallest]
        }

    private fun scrollTo(matcher: SemanticsMatcher) {
        homeList().performScrollToNode(matcher)
        compose.waitForIdle()
    }

    private companion object {
        const val TITLE = "Three ways in"
        const val PHRASE_BOX = "Describe a mix"
        val ROWS = listOf("Pick a ready-made night", "Type what you want", "Browse everything")
    }
}
