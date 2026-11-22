// SPDX-License-Identifier: MIT
package dev.nightfall

import android.service.quicksettings.Tile
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Playing → paused, through the tile's tap: the same pause the notification sends. */
@RunWith(AndroidJUnit4::class)
class TilePlayingToPauseTest : PlaybackHarness() {

    @Test
    fun aTapOnAPlayingTilePausesAndTheTileSaysSo() {
        compose.runOnIdle { runtime.play() }
        deliverStartCommand()
        await { runtime.status.value.playing }
        val listening = bindTile()
        await { listening.shown().state == Tile.STATE_ACTIVE }
        assertEquals("Playing", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_pause, listening.paintedGlyph())

        tapTile()

        await { !runtime.status.value.playing }
        assertFalse("a pause does not reset the mix", runtime.status.value.stopped)
        await { listening.shown().state == Tile.STATE_INACTIVE }
        assertEquals("Paused", listening.shown().subtitle?.toString())
        assertEquals(R.drawable.tile_play, listening.paintedGlyph())
        assertEquals(MixState.PAUSED, MixSurface.state(app))
        // The widget paints from the same record, so it moved with the tile.
        assertEquals("Paused", PlaybackWidget.wideViews(app, MixState.PAUSED, 1).stateLabel(app))
    }
}
