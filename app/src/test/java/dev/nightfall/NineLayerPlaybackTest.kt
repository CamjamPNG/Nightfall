// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The nine-layer scenario from the never-green instrumented test, on a JVM: the transport control
 * starts playback, the foreground service and its media session report it, and the mix survives
 * navigating away and recreating the activity.
 */
@RunWith(AndroidJUnit4::class)
class NineLayerPlaybackTest : PlaybackHarness() {
    @Test
    fun nineLayerPlaybackSurvivesNavigationAndActivityRecreation() {
        clickTransport("Play soundscape")
        val playback = deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }

        assertNull(runtime.status.value.error)
        assertEquals(9, runtime.saved.value.current.layers.size)
        assertTrue(
            "the audio thread wrote no samples",
            FloatSinkAudioTrack.floatsWritten.get() > 0,
        )
        assertTrue(
            "the audio thread wrote only silence (peak ${FloatSinkAudioTrack.peakWritten})",
            FloatSinkAudioTrack.peakWritten > 0f,
        )

        val service = shadowOf(playback)
        assertTrue(
            "playback never reached foreground",
            service.isLastForegroundNotificationAttached,
        )
        assertFalse(service.isForegroundStopped)
        assertFalse(service.isStoppedBySelf)

        val session = sessionOf(playback)
        assertEquals(true, session.player.playWhenReady)
        assertEquals(Player.STATE_READY, session.player.playbackState)
        assertEquals("Nine-Layer Dream", session.player.currentMediaItem?.mediaMetadata?.title)
        assertTrue(
            "media session did not report nine layers: " +
                "${session.player.currentMediaItem?.mediaMetadata?.artist}",
            session.player.currentMediaItem?.mediaMetadata?.artist?.startsWith("9 layers") == true,
        )

        compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
        await {
            compose.onAllNodesWithText("Sound library", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(runtime.status.value.playing)

        val writtenBefore = FloatSinkAudioTrack.floatsWritten.get()
        compose.activityRule.scenario.recreate()
        await {
            runtime.status.value.playing &&
                FloatSinkAudioTrack.floatsWritten.get() > writtenBefore
        }
        assertEquals(9, runtime.saved.value.current.layers.size)

        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        awaitTransport("Pause soundscape")
        assertEquals(0, FloatSinkAudioTrack.nonFiniteWrites.get())
        assertFalse(shadowOf(playback).isStoppedBySelf)
    }
}
