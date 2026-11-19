// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.PlaybackHarness
import dev.nightfall.R
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock

/**
 * The reveal gesture through the real Settings screen: the app's own window, its own LazyColumn and
 * its own version row, on a JVM.
 *
 * What is measured is the surface appearing, not a counter: four taps must leave Settings exactly as
 * it was, five inside three seconds must open it, and five spread over ten seconds must not. The last
 * case is the one that makes this a deliberate action rather than an accident — somebody scrolling and
 * tapping the bottom of the list should never land on a diagnostics switch.
 *
 * Time is advanced with the shadow clock rather than by sleeping, and every test that depends on time
 * passing first asserts that it moved, so a clock that silently refused to advance would fail here
 * instead of passing as if the gesture were simply inert.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsGestureTest : PlaybackHarness() {

    @Test
    fun fourTapsLeaveTheSurfaceHidden() {
        openSettings()
        val row = versionRow()
        repeat(4) { tap(row) }

        assertFalse("four taps revealed the diagnostics surface", surfaceVisible())
    }

    @Test
    fun fiveTapsInsideThreeSecondsRevealTheSurface() {
        openSettings()
        val row = versionRow()
        repeat(4) { tap(row) }
        advance(400)
        tap(row)

        assertTrue("five taps inside three seconds did not reveal the diagnostics surface", surfaceVisible())
        assertTrue(
            "the revealed surface does not name the switch",
            onScreen(compose.activity.getString(R.string.settings_diagnostics_switch)),
        )
        assertTrue(
            "the revealed surface does not say what it records",
            onScreen(compose.activity.getString(R.string.settings_diagnostics_collects), substring = true),
        )
        assertTrue(
            "the revealed surface does not say that nothing is recorded while the switch is off",
            onScreen(compose.activity.getString(R.string.settings_diagnostics_off), substring = true),
        )
        assertTrue(
            "the revealed surface does not say where the journal goes",
            onScreen(compose.activity.getString(R.string.settings_diagnostics_transport), substring = true),
        )
    }

    @Test
    fun fiveTapsOverTenSecondsLeaveTheSurfaceHidden() {
        openSettings()
        val row = versionRow()
        repeat(5) {
            tap(row)
            advance(2_500)
        }

        assertFalse("taps spread over ten seconds revealed the diagnostics surface", surfaceVisible())
    }

    /** Moves the clock the gesture reads, and proves it moved. */
    private fun advance(ms: Long) {
        val before = SystemClock.elapsedRealtime()
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        compose.waitForIdle()
        val moved = SystemClock.elapsedRealtime() - before
        assertTrue(
            "the test clock did not advance ($moved ms of $ms), so this scenario proves nothing",
            moved >= ms,
        )
    }

    private fun openSettings() {
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    /** The version row is the last item of a lazy list, so it is scrolled to before it is tapped. */
    private fun versionRow(): SemanticsNodeInteraction {
        compose
            .onAllNodes(hasScrollAction())
            .onFirst()
            .performScrollToNode(hasText("Free forever", substring = true))
        compose.waitForIdle()
        return compose.onNodeWithText("Free forever", substring = true)
    }

    private fun tap(row: SemanticsNodeInteraction) {
        row.performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun surfaceVisible() =
        onScreen(compose.activity.getString(R.string.settings_diagnostics_switch))

    private fun onScreen(text: String, substring: Boolean = false) =
        compose
            .onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
}
