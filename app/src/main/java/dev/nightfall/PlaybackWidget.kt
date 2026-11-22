// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews

/** What a tap does. Chosen when the widget is added, and editable afterwards. */
enum class WidgetTap {
    /** Act on the audio in place, with no app window. The default, and what the button is for. */
    TOGGLE,
    /** Open Nightfall and leave playback alone. */
    OPEN_APP,
}

/**
 * The home-screen widget: one play/pause control and the `RemoteViews` it draws.
 *
 * Two things are deliberately not here. The command path is the app's own media session, opened by
 * [SessionToggle] the way any controller opens it, so the widget cannot act on audio behind the
 * session's back. And nothing polls: [MixSurface.publish] is called by `PlaybackService` whenever
 * the runtime's status actually changes, and it redraws every placed widget from the state it just
 * recorded — the same record the Quick Settings tile paints from.
 */
object PlaybackWidget {
    const val ACTION_TOGGLE = "dev.nightfall.widget.action.TOGGLE"
    const val EXTRA_APP_WIDGET_ID = "appWidgetId"

    private const val PREFS = "nightfall_widget"
    private const val KEY_TAP = "tap_"

    /** The narrowest a widget can be and still hold the label beside the button, in dp. */
    private const val WIDE_MIN_DP = 90f

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    /** Redraws every placed widget from the state the runtime last published. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids =
            manager.getAppWidgetIds(
                ComponentName(context, PlaybackWidgetProvider::class.java)
            )
        if (ids.isEmpty()) return
        val state = MixSurface.state(context)
        for (id in ids) manager.updateAppWidget(id, views(context, state, id))
    }

    /**
     * The `RemoteViews` for one widget, in the variant that matches the size the launcher gave it:
     * from Android 12 a size-keyed map lets the launcher answer a resize without another update.
     */
    fun views(context: Context, state: MixState, appWidgetId: Int): RemoteViews {
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
    fun wideViews(context: Context, state: MixState, appWidgetId: Int): RemoteViews =
        bind(context, state, appWidgetId, R.layout.widget_nightfall)

    /** The 1x1 layout: the button alone, with the state in its glyph and its description. */
    fun tinyViews(context: Context, state: MixState, appWidgetId: Int): RemoteViews =
        bind(context, state, appWidgetId, R.layout.widget_nightfall_tiny)

    private fun bind(
        context: Context,
        state: MixState,
        appWidgetId: Int,
        layout: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        views.setImageViewResource(R.id.widget_button, glyph(state))
        views.setTextViewText(R.id.widget_state, context.getString(state.label))
        val spoken =
            context.getString(
                R.string.widget_state_spoken,
                context.getString(action(context, appWidgetId, state)),
                context.getString(state.label),
            )
        views.setContentDescription(R.id.widget_root, spoken)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, appWidgetId))
        return views
    }

    /** What a tap does here, which is not the same thing from one widget to the next. */
    private fun action(context: Context, appWidgetId: Int, state: MixState): Int =
        if (tap(context, appWidgetId) == WidgetTap.OPEN_APP) R.string.widget_action_open
        else state.tapAction

    private fun glyph(state: MixState): Int =
        if (state == MixState.PLAYING) R.drawable.widget_pause else R.drawable.widget_play

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
}
