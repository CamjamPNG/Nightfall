// SPDX-License-Identifier: MIT
package dev.nightfall

import android.service.quicksettings.Tile
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stopped → playing, through the tile's own tap. The tile starts nothing itself: the tap opens the
 * app's media session, exactly as the widget's does, and the audio comes out of the same `AudioHost`
 * the transport control reaches. That is also the answer for a tile added while nothing has played:
 * a tap is a plain play, so it starts the saved mix.
 */
@RunWith(AndroidJUnit4::class)
class TileStoppedToPlayTest : PlaybackHarness() {

    @Test
    fun aTapOnAStoppedTilePlaysTheSavedMixAndTheTileSaysSo() {
        val listening = bindTile()
        assertEquals("a fresh install is stopped", MixState.STOPPED, MixSurface.state(app))
        assertTrue("the runtime agrees nothing is loaded", runtime.status.value.stopped)
        assertEquals(Tile.STATE_INACTIVE, listening.shown().state)
        assertEquals("Stopped", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_play, listening.paintedGlyph())

        bindSessionService()
        FloatSinkAudioTrack.reset()
        tapTile()

        await { runtime.status.value.playing }
        awaitSamples()
        assertFalse("playing is not a stopped mix", runtime.status.value.stopped)
        assertEquals(MixState.PLAYING, MixSurface.state(app))

        // The picture the shade would hold now, not the one it was handed a moment ago.
        await { listening.shown().state == Tile.STATE_ACTIVE }
        assertEquals("Playing", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_pause, listening.paintedGlyph())
    }
}
