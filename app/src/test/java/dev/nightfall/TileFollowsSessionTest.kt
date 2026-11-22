// SPDX-License-Identifier: MIT
package dev.nightfall

import android.service.quicksettings.Tile
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tile's picture follows the session, whichever surface moved it, with no tap anywhere in this
 * test: the transport control, a media controller standing in for the notification and the lock
 * screen, a stop, and the service going away. It reads the platform's own tile object, so what is
 * asserted is what the shade would draw.
 */
@RunWith(AndroidJUnit4::class)
class TileFollowsSessionTest : PlaybackHarness() {

    @Test
    fun everyPlaybackChangeReachesTheTileWithoutATap() {
        val listening = bindTile()
        // A tile added before anything has played reads stopped, from the same record the widget uses.
        assertTrue("a fresh install is stopped", runtime.status.value.stopped)
        assertEquals(Tile.STATE_INACTIVE, listening.shown().state)
        assertEquals("Stopped", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_play, listening.paintedGlyph())

        compose.runOnIdle { runtime.play() }
        deliverStartCommand()
        await { runtime.status.value.playing }
        await { listening.shown().state == Tile.STATE_ACTIVE }
        assertEquals("Playing", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_pause, listening.paintedGlyph())

        // Home's own pause control.
        compose.runOnIdle { runtime.pause() }
        await { !runtime.status.value.playing }
        await { listening.shown().state == Tile.STATE_INACTIVE }
        assertEquals("Paused", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_play, listening.paintedGlyph())

        // A media controller — the notification or the lock screen — resumes it.
        val session = sessionOf(checkNotNull(service).get())
        compose.runOnIdle { session.player.play() }
        await { runtime.status.value.playing }
        await { listening.shown().state == Tile.STATE_ACTIVE }
        assertEquals("Playing", listening.shown().subtitle?.toString())

        // "Stop all" resets the mix. That is stopped, not paused.
        compose.runOnIdle { runtime.stop() }
        await { runtime.status.value.stopped }
        await { listening.shown().state == Tile.STATE_INACTIVE }
        assertEquals("Stopped", listening.shown().subtitle?.toString())

        // The service dies while playing: nothing is running afterwards, so neither is the tile.
        compose.runOnIdle { session.player.play() }
        await { listening.shown().state == Tile.STATE_ACTIVE }
        this.service?.destroy()
        this.service = null
        await { listening.shown().state == Tile.STATE_INACTIVE }
        assertEquals(
            "a released service leaves the tile stopped, whatever it last showed",
            "Stopped",
            listening.shown().subtitle?.toString(),
        )
    }
}
