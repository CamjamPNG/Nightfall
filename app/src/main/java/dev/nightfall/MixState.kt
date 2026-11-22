// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.annotation.StringRes
import androidx.media3.common.Player

/**
 * What a control shows: the mix's state, as the runtime reports it.
 *
 * One mapping for every surface that shows playback — the home-screen widget and the Quick Settings
 * tile — read from the same status the transport control draws. No surface keeps its own idea of
 * whether audio is running, so the two cannot drift into disagreeing about it.
 */
enum class MixState {
    PLAYING,
    PAUSED,
    STOPPED;

    companion object {
        fun of(status: PlaybackStatus): MixState =
            when {
                status.playing -> PLAYING
                status.stopped -> STOPPED
                else -> PAUSED
            }
    }
}

/** The state's name, which is the same word on every surface that shows it. */
@get:StringRes
val MixState.label: Int
    get() =
        when (this) {
            MixState.PLAYING -> R.string.widget_state_playing
            MixState.PAUSED -> R.string.widget_state_paused
            MixState.STOPPED -> R.string.widget_state_stopped
        }

/** What a tap on a play/pause control offers here, reusing the transport control's own copy. */
@get:StringRes
val MixState.tapAction: Int
    get() = if (this == MixState.PLAYING) R.string.home_action_pause else R.string.home_action_play

/**
 * The whole command surface of a play/pause control: one `play` or `pause` on the app's media
 * session, which is the same thing the transport control and the notification do.
 *
 * The decision is read from the session's own player rather than from what a surface last drew, so
 * a tap on a picture that has fallen behind still sends the command that changes what is playing.
 */
fun togglePlayback(player: Player) {
    if (player.playWhenReady) player.pause() else player.play()
}
