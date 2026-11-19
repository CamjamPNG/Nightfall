// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A stored state belongs to the boot it was written in. After a power loss the widget must read as
 * stopped — and keep reading as stopped for the rest of that boot, not only until uptime overtakes
 * the old mark, which is the window a skip-over guard leaves open.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRebootStateTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun aStateFromAnEarlierBootNeverComesBack() {
        // As if playback was written an hour into the previous boot and the phone then lost power.
        val previousBootMark = android.os.SystemClock.elapsedRealtime() + HOUR_MS
        prefs().edit().putString("state", WidgetState.PLAYING.name).putLong("state_at", previousBootMark).commit()

        assertEquals(WidgetState.STOPPED, PlaybackWidget.state(app))
        // The read replaced the value. Had it only skipped past it, this boot would show "playing"
        // again once its own uptime passed the old mark — which is exactly what the clock below does.
        advance(HOUR_MS * 2)
        assertEquals(
            "a stored state from an earlier boot came back",
            WidgetState.STOPPED,
            PlaybackWidget.state(app),
        )
        assertEquals(
            "the read left the invalid state on disk",
            WidgetState.STOPPED.name,
            prefs().getString("state", null),
        )
    }

    @Test
    fun aStateFromThisBootIsTrusted() {
        prefs()
            .edit()
            .putString("state", WidgetState.PAUSED.name)
            .putLong("state_at", android.os.SystemClock.elapsedRealtime())
            .commit()
        assertEquals(WidgetState.PAUSED, PlaybackWidget.state(app))
        advance(HOUR_MS)
        assertEquals("a paused widget stays paused", WidgetState.PAUSED, PlaybackWidget.state(app))
        assertNotEquals(WidgetState.STOPPED, PlaybackWidget.state(app))
    }

    private fun prefs() = app.getSharedPreferences("nightfall_widget", Context.MODE_PRIVATE)

    /** Advances Robolectric's clock, and with it `SystemClock.elapsedRealtime`. */
    private fun advance(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
