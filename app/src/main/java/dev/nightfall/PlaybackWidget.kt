// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.widget.RemoteViews
import androidx.media3.common.Player

/** What the widget shows: the mix's state, as the runtime reports it. */
enum class WidgetState {
    PLAYING,
    PAUSED,
    STOPPED;

    companion object {
        /**
         * The widget's picture of the runtime, read from the same status the transport control draws:
         * the widget never keeps its own idea of whether audio is running.
         */
        fun of(status: PlaybackStatus): WidgetState =
            when {
                status.playing -> PLAYING
                status.stopped -> STOPPED
                else -> PAUSED
            }
    }
}

/** What a tap does. Chosen when the widget is added, and editable afterwards. */
enum class WidgetTap {
    /** Act on the audio in place, with no app window. The default, and what the button is for. */
    TOGGLE,
    /** Open Nightfall and leave playback alone. */
    OPEN_APP,
}

/**
 * The home-screen widget: one play/pause control, its stored state, and the `RemoteViews` it draws.
 *
 * Two things are deliberately not here. The command path is the app's own media session, opened by
 * [WidgetSession] the way any controller opens it, so the widget cannot act on audio behind the
 * session's back. And nothing polls: [publish] is called by `PlaybackService` whenever the runtime's
 * status actually changes, so the state shown is the state the service last reported.
 */
object PlaybackWidget {
    const val ACTION_TOGGLE = "dev.nightfall.widget.action.TOGGLE"
    const val EXTRA_APP_WIDGET_ID = "appWidgetId"

    private const val PREFS = "nightfall_widget"
    private const val KEY_STATE = "state"
    private const val KEY_STATE_AT = "state_at"
    private const val KEY_TAP = "tap_"

    /**
     * `elapsedRealtime` counts from boot. A stored mark that is ahead of now belongs to an earlier
     * boot than this one, and audio cannot survive a reboot, so the state is stopped rather than
     * whatever the widget was showing when the phone went down.
     */
    private const val REBOOT_SLACK_MS = 5_000L

    /** The narrowest a widget can be and still hold the label beside the button, in dp. */
    private const val WIDE_MIN_DP = 90f

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What was last written, before the reboot guard has a say. */
    private fun stored(context: Context): WidgetState? {
        val name = prefs(context).getString(KEY_STATE, null) ?: return null
        return WidgetState.entries.firstOrNull { it.name == name }
    }

    fun tap(context: Context, appWidgetId: Int): WidgetTap {
        val stored = prefs(context).getString(KEY_TAP + appWidgetId, null) ?: return WidgetTap.TOGGLE
        return WidgetTap.entries.firstOrNull { it.name == stored } ?: WidgetTap.TOGGLE
    }

    fun setTap(context: Context, appWidgetId: Int, tap: WidgetTap) {
        prefs(context).edit().putString(KEY_TAP + appWidgetId, tap.name).apply()
    }

    /** A removed widget carries its tap choice with it. */
    fun forget(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(KEY_TAP + appWidgetId).apply()
    }

    fun state(context: Context): WidgetState {
        val state = stored(context) ?: return WidgetState.STOPPED
        val at = prefs(context).getLong(KEY_STATE_AT, 0L)
        if (state != WidgetState.STOPPED && SystemClock.elapsedRealtime() + REBOOT_SLACK_MS < at) {
            // The mark belongs to a boot this one never reached, so no audio is running: the stored
            // picture is wrong, not merely old. Replace it rather than skip past it, or this same
            // boot resurrects "playing" once uptime overtakes the old mark.
            record(context, WidgetState.STOPPED)
            return WidgetState.STOPPED
        }
        return state
    }

    private fun record(context: Context, state: WidgetState) {
        prefs(context)
            .edit()
            .putString(KEY_STATE, state.name)
            .putLong(KEY_STATE_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    /**
     * Records the state and redraws every placed widget, if it is news. Called on the main thread
     * with each status the runtime publishes, so the comparison is what keeps a quarter-second meter
     * tick from touching the launcher.
     */
    fun publish(context: Context, state: WidgetState) {
        if (stored(context) == state) return
        record(context, state)
        refresh(context)
    }

    /** Redraws every placed widget from the stored state. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids =
            manager.getAppWidgetIds(
                ComponentName(context, PlaybackWidgetProvider::class.java)
            )
        if (ids.isEmpty()) return
        val state = state(context)
        for (id in ids) manager.updateAppWidget(id, views(context, state, id))
    }

    /**
     * The `RemoteViews` for one widget, in the variant that matches the size the launcher gave it:
     * from Android 12 a size-keyed map lets the launcher answer a resize without another update.
     */
    fun views(context: Context, state: WidgetState, appWidgetId: Int): RemoteViews {
        val wide = wideViews(context, state, appWidgetId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return if (cellWidth(context, appWidgetId) < WIDE_MIN_DP)
                tinyViews(context, state, appWidgetId)
            else wide
        return RemoteViews(
            mapOf(
                SizeF(0f, 0f) to tinyViews(context, state, appWidgetId),
                SizeF(WIDE_MIN_DP, 0f) to wide,
            )
        )
    }

    /**
     * The width the launcher last reported for this widget, in dp. Before Android 12 there is no
     * size-keyed map, so this is the only thing that knows the widget is one cell and not two.
     */
    private fun cellWidth(context: Context, appWidgetId: Int): Int =
        AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)

    /** The roughly 2x1 layout: the button and the state's name. */
    fun wideViews(context: Context, state: WidgetState, appWidgetId: Int): RemoteViews =
        bind(context, state, appWidgetId, R.layout.widget_nightfall)

    /** The 1x1 layout: the button alone, with the state in its glyph and its description. */
    fun tinyViews(context: Context, state: WidgetState, appWidgetId: Int): RemoteViews =
        bind(context, state, appWidgetId, R.layout.widget_nightfall_tiny)

    private fun bind(
        context: Context,
        state: WidgetState,
        appWidgetId: Int,
        layout: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        views.setImageViewResource(R.id.widget_button, glyph(state))
        views.setTextViewText(R.id.widget_state, context.getString(label(state)))
        val spoken =
            context.getString(
                R.string.widget_state_spoken,
                context.getString(action(context, appWidgetId, state)),
                context.getString(label(state)),
            )
        views.setContentDescription(R.id.widget_root, spoken)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, appWidgetId))
        return views
    }

    /** What a tap does here, which is not the same thing from one widget to the next. */
    private fun action(context: Context, appWidgetId: Int, state: WidgetState): Int =
        when {
            tap(context, appWidgetId) == WidgetTap.OPEN_APP -> R.string.widget_action_open
            state == WidgetState.PLAYING -> R.string.home_action_pause
            else -> R.string.home_action_play
        }

    private fun glyph(state: WidgetState): Int =
        if (state == WidgetState.PLAYING) R.drawable.widget_pause else R.drawable.widget_play

    private fun label(state: WidgetState): Int =
        when (state) {
            WidgetState.PLAYING -> R.string.widget_state_playing
            WidgetState.PAUSED -> R.string.widget_state_paused
            WidgetState.STOPPED -> R.string.widget_state_stopped
        }

    /**
     * The tap. In [WidgetTap.OPEN_APP] the widget hands the launcher an activity intent, so no part
     * of Nightfall runs until the person is in the app; otherwise it is a broadcast to this app's
     * provider, which is the only thing that opens the media session.
     */
    fun tapIntent(context: Context, appWidgetId: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (tap(context, appWidgetId) == WidgetTap.OPEN_APP)
            PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            )
        else
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, PlaybackWidgetProvider::class.java)
                    .setAction(ACTION_TOGGLE)
                    .putExtra(EXTRA_APP_WIDGET_ID, appWidgetId),
                flags,
            )
    }

    /**
     * The tap, against the session's own state: playing stops, anything else starts. This is the
     * whole command surface of the widget — one `play` or `pause` on the app's media session, which
     * is the same thing the transport control and the notification do.
     */
    fun toggle(player: Player) {
        if (player.playWhenReady) player.pause() else player.play()
    }
}
