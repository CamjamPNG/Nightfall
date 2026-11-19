// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The switch that turns endless night on: it is on the mix screen where a person edits their mix, it
 * writes the mix, the night survives a save, and playback keeps running with the extra beds the mixer
 * starts carrying.
 *
 * A screenshot cannot answer any of that — a frame is the same whether the switch writes anything — so
 * the assertions are about state and about the audio thread staying healthy.
 */
@RunWith(AndroidJUnit4::class)
class EndlessNightToggleTest : PlaybackHarness() {

    private val switch = hasContentDescription(TITLE)

    /**
     * The screen is a lazy list, so a control below the fold is not composed at all and cannot be
     * clicked until it has been scrolled to. Two scrollables are on the screen — the list itself and
     * the preset strip inside it — and only the outer one scrolls to the switch.
     */
    private fun scrollTo(label: SemanticsMatcher) =
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(label)

    private fun toggle(value: Boolean) {
        scrollTo(switch)
        compose.onNode(switch).performClick()
        await { runtime.saved.value.current.endless.enabled == value }
    }

    /** Play and pause live at the top of the list, above the switch: scroll back before pressing. */
    private fun pressTransport(label: String) {
        scrollTo(hasContentDescription(label))
        clickTransport(label)
    }

    /** The layer count the media notification announces: the person's own layers, never the night's. */
    private fun artistLayers(session: MediaSession): Int? =
        Regex("""^(\d+) layers""")
            .find(session.player.currentMediaItem?.mediaMetadata?.artist ?: "")
            ?.groupValues
            ?.get(1)
            ?.toInt()

    @Test
    fun theSwitchTurnsTheNightOnAndPlaysWithIt() {
        awaitRestoredAndLoadNineLayerMix()
        assertFalse("a mix starts with the night off", runtime.saved.value.current.endless.enabled)

        toggle(true)
        awaitSaved()
        assertTrue(runtime.saved.value.current.endless.enabled)
        // The switch is on the mix, not a setting, so it travels with the mix when it is saved.
        assertEquals("the seed of a switched-on night", 1L, runtime.saved.value.current.endless.seed)

        pressTransport("Play soundscape")
        val session = sessionOf(deliverStartCommand())
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }

        // The mixer carries the person's nine layers plus the night's companions, and none of it shows
        // up in the layer count the notification reports.
        val written = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > written + 48000 }
        assertTrue("playback stopped with the night on", runtime.status.value.playing)
        assertTrue(FloatSinkAudioTrack.peakWritten > 0f)
        assertEquals("the night wrote a non-finite sample", 0, FloatSinkAudioTrack.nonFiniteWrites.get())
        await { artistLayers(session) == 9 }

        toggle(false)
        val afterOff = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > afterOff + 24000 }
        assertTrue("turning the night off stopped playback", runtime.status.value.playing)
        assertEquals(0, FloatSinkAudioTrack.nonFiniteWrites.get())
        awaitSaved()
        assertNull(runtime.status.value.error)
    }

    private companion object {
        /** The switch's own label: what a screen reader announces for it. */
        const val TITLE = "Endless night"
    }
}
