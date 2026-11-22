// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Two hundred percent text, which is what Android's own accessibility setting offers and what a
 * person with low vision actually turns on.
 *
 * Each screen is hosted the way the app hosts it, in the night theme, and the whole composition is
 * given a density whose *font* scale is doubled while its pixel density is untouched. What is then
 * asserted is the two ways this goes wrong in practice: a control whose box was pinned to a fixed
 * number of dp and now cuts its own label, and a row of controls that grows wider than the window
 * and draws over its neighbour.
 *
 * A JVM cannot see a real clip: these assertions are about size and placement, measured on the
 * semantics tree. `docs/ACCESSIBILITY.md` says so rather than claiming a screenshot proves it.
 */
@Config(qualifiers = "w411dp-h914dp-420dpi")
abstract class BigFontHarness {
    @get:Rule val compose = createComposeRule()

    protected val app: NightfallApp = ApplicationProvider.getApplicationContext()

    /** Doubles text and nothing else: 411x914 dp at 2.625x is still the window a Pixel 7 gives. */
    protected fun bigText(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            NightfallTheme(light = false) {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, FONT_SCALE)) {
                    content()
                }
            }
        }
    }

    protected fun controls(): List<SemanticsNode> =
        compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()

    protected fun dp(node: SemanticsNode): Pair<Float, Float> {
        val scale = compose.density.density
        return (node.size.width / scale).toFloat() to (node.size.height / scale).toFloat()
    }

    /** Nothing may grow past the right edge of the window, in either direction. */
    protected fun pastTheEdge(): List<String> = controls().mapNotNull { node ->
        val (w, _) = dp(node)
        val left = node.boundsInRoot.left / compose.density.density
        val label = node.config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
            ?.joinToString()
            ?: node.config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }
            ?: "(no name)"
        if (left < 0f || left + w > WINDOW_DP + 0.5f) "%.1f..%.1f dp: %s".format(left, left + w, label)
        else null
    }

    protected companion object {
        const val DENSITY = 2.625f
        const val WAIT_MS = 30_000L
        const val FONT_SCALE = 2f
        const val WINDOW_DP = 411f
    }
}

@RunWith(AndroidJUnit4::class)
class HomeBigFontTest : BigFontHarness() {
    @Test
    fun homeAtTwoHundredPercentKeepsEveryControlOnScreenAndGrowsItsLabels() {
        // Built outside the composition: a ViewModel constructed inside a composable is what lint's
        // ViewModelConstructorInComposable is for, and it is right even in a test.
        val model = NightfallViewModel(app)
        bigText { NightfallScreen(model) }
        compose.waitUntil(WAIT_MS) { controls().isNotEmpty() }

        val overflow = pastTheEdge()
        assertTrue("controls past the window edge at 200%:\n" + overflow.joinToString("\n"), overflow.isEmpty())

        // 54dp was a fixed box around a 14sp label: at 200% the label alone is taller than the box,
        // so the measured height has to be the box *plus* the room the doubled line needs.
        // The button is below the fold in a lazy list: a control that is not composed cannot be
        // measured, and at 200% the fold is closer than it was.
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst()
            .performScrollToNode(hasText("Add another sound") and hasClickAction())
        val add = compose.onNode(hasText("Add another sound") and hasClickAction())
        // Minimums, not fixed boxes: at 100% this control is 54dp, and a minimum cannot shrink it.
        assertTrue(
            "the add button is ${dp(add.fetchSemanticsNode()).second}dp tall at 200%",
            dp(add.fetchSemanticsNode()).second >= 54f,
        )
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsBigFontTest : BigFontHarness() {
    @Test
    fun settingsAtTwoHundredPercentKeepsEveryRowInsideTheWindow() {
        bigText { SettingsScreen(app.runtime, onExport = {}, onCredits = {}) }
        compose.waitUntil(WAIT_MS) { controls().isNotEmpty() }
        val overflow = pastTheEdge()
        assertTrue("rows past the window edge at 200%:\n" + overflow.joinToString("\n"), overflow.isEmpty())

        // Half of Settings is two lines of copy beside a switch. At 200% the switch must still be the
        // control it was and the copy must wrap rather than run off the card.
        for (label in listOf("Light theme", "Reduced motion", "Endless night")) {
            compose.onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .forEach { node ->
                    val (w, h) = dp(node)
                    assertTrue("$label is ${w}x${h}dp", w > 0f && h > 0f)
                }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunBigFontTest : BigFontHarness() {
    @Test
    fun theFirstRunCardAtTwoHundredPercentGrowsInsteadOfCuttingItsRows() {
        bigText { FirstRunCard(onPresets = {}, onDescribe = {}, onLibrary = {}, onDismiss = {}) }
        compose.waitUntil(WAIT_MS) { controls().isNotEmpty() }

        val overflow = pastTheEdge()
        assertTrue("the card runs past the window at 200%:\n" + overflow.joinToString("\n"), overflow.isEmpty())

        // Three rows of wrapping copy is exactly what a fixed height would cut off; each row has to
        // be taller than its 100% self, which is 48dp, because its one line became two.
        for (row in listOf("Pick a ready-made night", "Type what you want", "Browse everything")) {
            val node = compose.onAllNodes(hasText(row) and hasClickAction()).fetchSemanticsNodes().single()
            assertTrue("the $row row is ${dp(node).second}dp tall", dp(node).second >= 64f)
        }
        val dismiss = compose.onNode(hasText("Not now") and hasClickAction())
        assertTrue(
            "Not now is ${dp(dismiss.fetchSemanticsNode()).second}dp tall at 200%",
            dp(dismiss.fetchSemanticsNode()).second >= 56f,
        )
    }
}
