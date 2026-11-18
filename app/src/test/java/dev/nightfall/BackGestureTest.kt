// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The back gesture, now that Android 16 runs predictive back by default for apps targeting 36 and no
 * longer dispatches `KeyEvent.KEYCODE_BACK` or calls `onBackPressed`.
 *
 * The app intercepts back with `BackHandler`, which registers on `OnBackPressedDispatcher` — the API
 * predictive back drives — rather than the key event, so a gesture on a tab returns to Home and a
 * gesture on Home closes the screen. What matters for a sleep app is the last assertion: closing the
 * screen must not end the night, because the audio thread lives in the application and the media
 * session holds the foreground service.
 *
 * The dispatcher is driven by hand here: Robolectric has no gesture, and the dispatcher is exactly
 * what the system's predictive-back animation invokes once the gesture commits.
 */
@RunWith(AndroidJUnit4::class)
class BackGestureTest : PlaybackHarness() {

    @Test
    fun backWalksHomeThenClosesTheScreenWithoutStoppingTheNight() {
        clickTransport("Play soundscape")
        val playback = deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }

        open("Library")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(
            "back on a tab closed the screen instead of returning to Home",
            compose.activity.isFinishing,
        )
        assertTrue("back on a tab did not return to Home", onScreen("Nightfall"))

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue("back on Home did not close the screen", compose.activity.isFinishing)

        val written = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > written }
        assertTrue("closing the screen stopped playback", runtime.status.value.playing)
        assertFalse(
            "closing the screen stopped the foreground service",
            shadowOf(playback).isStoppedBySelf,
        )
        compose.runOnIdle { runtime.stop() }
    }

    /** Opens a tab by its label and waits for the screen behind it. */
    private fun open(tab: String) {
        compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun onScreen(text: String) =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
