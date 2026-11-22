// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
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
 * stopped, and keep reading as stopped — however far into the new boot the launcher asks, not only
 * until uptime overtakes the old mark.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRebootStateTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun aStateWrittenInThisBootIsTrusted() {
        MixSurface.publish(app, MixState.PAUSED)
        assertEquals(MixState.PAUSED, MixSurface.state(app))
        advance(HOUR_MS * 3)
        assertEquals("a paused widget stays paused", MixState.PAUSED, MixSurface.state(app))
        assertNotEquals(MixState.STOPPED, MixSurface.state(app))
    }

    @Test
    fun aStateFromAnotherBootNeverComesBack() {
        // As if the phone was playing and then lost power: the state carries the previous boot's
        // identifier, and its uptime mark is small because that boot was short.
        val shortPreviousBoot = SystemClock.elapsedRealtime() + 60_000
        prefs()
            .edit()
            .putString("state", MixState.PLAYING.name)
            .putLong("state_at", shortPreviousBoot)
            .putString("boot", "not-this-boot")
            .commit()

        assertEquals(MixState.STOPPED, MixSurface.state(app))
        assertEquals(
            "the read left the invalid state on disk",
            MixState.STOPPED.name,
            prefs().getString("state", null),
        )
        // Had the guard only compared uptime, this is where "playing" would return: two hours in,
        // the old mark is long behind us. It must not.
        advance(HOUR_MS * 2)
        assertEquals(
            "a stored state from another boot came back",
            MixState.STOPPED,
            MixSurface.state(app),
        )
    }

    @Test
    fun aStateWithNoBootIdentifierAndAnUnreachableMarkIsStopped() {
        // A state written before this build recorded boot identifiers.
        prefs()
            .edit()
            .putString("state", MixState.PLAYING.name)
            .putLong("state_at", SystemClock.elapsedRealtime() + HOUR_MS)
            .commit()

        assertEquals(MixState.STOPPED, MixSurface.state(app))
        assertEquals(MixState.STOPPED.name, prefs().getString("state", null))
    }

    private fun prefs() = app.getSharedPreferences("nightfall_widget", Context.MODE_PRIVATE)

    /** Advances Robolectric's clock, and with it `SystemClock.elapsedRealtime`. */
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
