// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens the app's media session the way a controller does and hands it a tap.
 *
 * Both surfaces that toggle playback from outside the app — the home-screen widget and the Quick
 * Settings tile — reach audio through this one function, and it reaches audio through the session
 * the notification, the lock screen and the transport control use. There is no second player and no
 * second command channel.
 *
 * A bound controller that connects to a session service which is not running starts that service, so
 * this is also the cold path: with no Nightfall process, a tap creates the service, and the session
 * plays through the same `AudioHost` the app's own Play button reaches. A widget tap is one of the
 * platform's documented exemptions for starting a foreground service from the background.
 */
object SessionToggle {
    private const val TIMEOUT_MS = 5_000L

    fun toggle(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val settled = AtomicBoolean(false)
        val settle = {
            if (settled.compareAndSet(false, true)) onDone()
        }
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        // A session that never answers must not hold the caller open: the surface then shows what
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
                        togglePlayback(controller)
                    } finally {
                        controller.release()
                    }
                } catch (e: Exception) {
                    // Nothing to act on. PlaybackService records why; the surface stays with the
                    // state the runtime last published rather than inventing one.
                }
                handler.removeCallbacks(timeout)
                settle()
            },
            ContextCompat.getMainExecutor(app),
        )
    }
}
