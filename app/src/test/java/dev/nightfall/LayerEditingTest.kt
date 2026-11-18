// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editing the mix while it plays: the running audio thread has to pick up added and removed layers
 * without stopping, and the media session has to report the new layer count.
 */
@RunWith(AndroidJUnit4::class)
class LayerEditingTest : PlaybackHarness() {
    @Test
    fun addingAndRemovingLayersWhilePlayingKeepsTheStreamRunning() {
        clickTransport("Play soundscape")
        val playback = deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }
        val session = sessionOf(playback)
        await { artistLayers(session) == 9 }

        val added = Library.all.first { it.available && it.id != "432-hz-ambient-music" }
        compose.runOnIdle { runtime.add(added) }
        await { runtime.saved.value.current.layers.size == 10 }
        await { artistLayers(session) == 10 }

        val writtenAfterAdd = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > writtenAfterAdd }
        assertTrue("playback stopped when a layer was added", runtime.status.value.playing)
        assertTrue(FloatSinkAudioTrack.peakWritten > 0f)

        val kept = runtime.saved.value.current.layers.drop(3)
        compose.runOnIdle { runtime.mix { it.copy(layers = kept) } }
        await { runtime.saved.value.current.layers.size == 7 }
        await { artistLayers(session) == 7 }

        val writtenAfterRemove = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > writtenAfterRemove }
        assertTrue("playback stopped when layers were removed", runtime.status.value.playing)
        assertTrue(FloatSinkAudioTrack.peakWritten > 0f)
        assertEquals(0, FloatSinkAudioTrack.nonFiniteWrites.get())
        // The error is the app's word on the last save, so read it after the edits are on disk
        // rather than at whatever moment this test happens to reach the assertion.
        awaitSaved()
        assertNull(runtime.status.value.error)
    }

    private fun artistLayers(session: MediaSession): Int? =
        Regex("""^(\d+) layers""")
            .find(session.player.currentMediaItem?.mediaMetadata?.artist ?: "")
            ?.groupValues
            ?.get(1)
            ?.toInt()
}
