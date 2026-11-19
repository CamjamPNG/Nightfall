// SPDX-License-Identifier: MIT
package dev.nightfall

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The widget's receiver. It runs nothing of its own: a tap opens the app's media session — the same
 * session the notification, the lock screen and the transport control use — and asks it to change.
 *
 * Updates are pushed from [PlaybackService] rather than requested on a period, so a widget that is
 * placed, resized or restored by the launcher just repaints from the state the service last wrote.
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
        WidgetSession.toggle(context) { finishing.finish() }
    }
}

/**
 * Opens the app's media session the way a controller does and hands it the tap.
 *
 * A bound controller that connects to a session service which is not running starts that service, so
 * this is also the cold path: with no Nightfall process, a tap creates the service, and the session
 * plays through the same `AudioHost` the app's own Play button reaches. A widget tap is one of the
 * platform's documented exemptions for starting a foreground service from the background.
 */
object WidgetSession {
    private const val TIMEOUT_MS = 5_000L

    fun toggle(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val settled = AtomicBoolean(false)
        val settle = {
            if (settled.compareAndSet(false, true)) onDone()
        }
        val token =
            SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        // A session that never answers must not hold the broadcast open: the widget then shows what
        // the runtime last reported, which is the truth about audio that is not running.
        val timeout =
            Runnable {
                MediaController.releaseFuture(future)
                settle()
            }
        handler.postDelayed(timeout, TIMEOUT_MS)
        future.addListener(
            {
                try {
                    val controller = future.get()
                    try {
                        PlaybackWidget.toggle(controller)
                    } finally {
                        controller.release()
                    }
                } catch (e: Exception) {
                    // Nothing to act on. PlaybackService records why; the widget stays with the state
                    // the runtime last published rather than inventing one.
                }
                handler.removeCallbacks(timeout)
                settle()
            },
            ContextCompat.getMainExecutor(app),
        )
    }
}
