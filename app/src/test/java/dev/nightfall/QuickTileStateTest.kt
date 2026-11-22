// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The mapping between what the runtime reports and what the tile shows, in both directions and
 * without a service: the status becomes a state, and the state becomes the picture a tap is offered.
 * The widget paints from the same state and the same copy, which is why neither surface carries a
 * `when` of its own.
 */
@RunWith(AndroidJUnit4::class)
class QuickTileStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyStatusTheRuntimeReportsBecomesOneState() {
        assertEquals(
            "a fresh or stopped mix",
            MixState.STOPPED,
            MixState.of(PlaybackStatus(playing = false, stopped = true)),
        )
        assertEquals(
            "playing is playing",
            MixState.PLAYING,
            MixState.of(PlaybackStatus(playing = true, stopped = false)),
        )
        assertEquals(
            "silent but resumable is paused, not stopped",
            MixState.PAUSED,
            MixState.of(PlaybackStatus(playing = false, stopped = false)),
        )
        // `stopped` is whatever stop() last did, so a playing runtime that still carries the flag is
        // playing: the live fact wins, or a stop would keep the tile saying paused.
        assertEquals(
            "playing outranks a stale stopped flag",
            MixState.PLAYING,
            MixState.of(PlaybackStatus(playing = true, stopped = true)),
        )
    }

    @Test
    fun eachStatePaintsItsPictureAndOffersTheOppositeCommand() {
        for (state in MixState.entries) {
            assertEquals(
                "only a playing mix reads as on",
                state == MixState.PLAYING,
                QuickTile.on(state),
            )
            assertEquals(
                "the glyph is what a tap will do, not what the mix is doing",
                if (state == MixState.PLAYING) R.drawable.tile_pause else R.drawable.tile_play,
                QuickTile.glyph(state),
            )
            assertEquals("the tile names the state", state.label, QuickTile.subtitle(state))
            assertEquals(
                "a tap on a playing mix pauses, and a tap on anything else plays",
                if (state == MixState.PLAYING) R.string.home_action_pause
                else R.string.home_action_play,
                state.tapAction,
            )
        }
        assertEquals("Playing", context.getString(MixState.PLAYING.label))
        assertEquals("Paused", context.getString(MixState.PAUSED.label))
        assertEquals("Stopped", context.getString(MixState.STOPPED.label))
    }
}
