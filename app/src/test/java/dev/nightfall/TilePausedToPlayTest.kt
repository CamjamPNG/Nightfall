// SPDX-License-Identifier: MIT
package dev.nightfall

import android.service.quicksettings.Tile
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Paused → playing, through the tile's tap: a resume, not a restart. */
@RunWith(AndroidJUnit4::class)
class TilePausedToPlayTest : PlaybackHarness() {

    @Test
    fun aTapOnAPausedTileResumesAndTheTileSaysSo() {
        compose.runOnIdle { runtime.play() }
        deliverStartCommand()
        await { runtime.status.value.playing }
        val listening = bindTile()
        await { listening.shown().state == Tile.STATE_ACTIVE }

        compose.runOnIdle { runtime.pause() }
        await { !runtime.status.value.playing && !runtime.status.value.stopped }
        await { listening.shown().state == Tile.STATE_INACTIVE }
        assertEquals("Paused", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_play, listening.paintedGlyph())

        FloatSinkAudioTrack.reset()
        tapTile()

        await { runtime.status.value.playing }
        awaitSamples()
        assertFalse("a resume is not a stopped mix", runtime.status.value.stopped)
        assertEquals(MixState.PLAYING, MixSurface.state(app))
        await { listening.shown().state == Tile.STATE_ACTIVE }
        assertEquals("Playing", listening.shown().subtitle?.toString())
    }
}
