// SPDX-License-Identifier: MIT
package dev.nightfall

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * The widget's receiver. It runs nothing of its own: a tap opens the app's media session — the same
 * session the notification, the lock screen and the transport control use — and asks it to change.
 *
 * Updates are pushed from [PlaybackService] through [MixSurface] rather than requested on a period,
 * so a widget that is placed, resized or restored by the launcher just repaints from the state the
 * service last wrote.
 */
class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        PlaybackWidget.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        PlaybackWidget.refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) PlaybackWidget.forget(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlaybackWidget.ACTION_TOGGLE) {
            super.onReceive(context, intent)
            return
        }
        // Connecting a controller is asynchronous, and the process owns nothing else that would keep
        // it alive while it happens.
        val finishing = goAsync()
        SessionToggle.toggle(context) { finishing.finish() }
    }
}
