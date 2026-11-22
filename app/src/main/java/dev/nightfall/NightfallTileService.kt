// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Nightfall in Quick Settings: one tile that says what the soundscape is doing and toggles it
 * without unlocking anything.
 *
 * There is one control path here and one picture of the runtime, both shared with the widget. A tap
 * opens the app's media session with the same [SessionToggle] the widget's tap uses and sends one
 * `play` or `pause`; nothing on this path starts an activity, so the 33+ `PendingIntent` overload
 * of `startActivityAndCollapse` is never reached and nothing can crash on it. Nothing polls either:
 * [onStartListening] paints from [MixSurface]'s record of what the service last published, and every
 * publish repaints a listening tile, so playback started from the app, the widget, the notification,
 * a Bluetooth button or this tile shows up here.
 *
 * A tile added while nothing has played reads stopped, from the same record that tells a widget so.
 * A tap there is a plain `play` on the session, which starts the saved mix the way the widget's tap
 * does — there is no separate "restore last mix" path, and none is needed.
 */
class NightfallTileService : TileService() {

    override fun onStartListening() {
        listening = this
        paint()
    }

    override fun onStopListening() {
        if (listening === this) listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        if (listening === this) listening = null
        super.onDestroy()
    }

    override fun onClick() {
        // The shade collapses when this returns, so the session change runs on this process's main
        // thread exactly as the widget's broadcast does — nothing here waits on the tile.
        SessionToggle.toggle(this) {}
    }

    /** Paints this tile from the record, so the tile and the widget can never disagree. */
    internal fun paint() {
        val tile = qsTile ?: return
        val state = MixSurface.state(this)
        tile.state = if (QuickTile.on(state)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, QuickTile.glyph(state))
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(QuickTile.subtitle(state))
            tile.contentDescription =
                getString(
                    R.string.widget_state_spoken,
                    getString(state.tapAction),
                    getString(state.label),
                )
        }
        tile.updateTile()
    }

    companion object {
        /**
         * The tile instance the system is listening through, if any. The shade is the only thing
         * that creates one, and a closed shade has nothing to repaint: it paints on its next
         * `onStartListening` instead.
         */
        @Volatile private var listening: NightfallTileService? = null

        /** Repaints a tile the shade has open, and does nothing when it has not. */
        fun refresh() {
            listening?.paint()
        }
    }
}

/**
 * The tile's picture of one state: whether it reads as on, the glyph for what a tap will do, and
 * the words it shows. Pure, so what the tile asserts is testable without a shade to put it in.
 */
object QuickTile {
    fun on(state: MixState): Boolean = state == MixState.PLAYING

    /** A tap pauses what is playing and starts what is not, so the glyph is the action, not the state. */
    @DrawableRes
    fun glyph(state: MixState): Int =
        if (state == MixState.PLAYING) R.drawable.tile_pause else R.drawable.tile_play

    @StringRes fun subtitle(state: MixState): Int = state.label
}
