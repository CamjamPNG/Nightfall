// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget's picture follows the session, whichever surface moved it: the app's transport, the
 * media session behind the notification and the lock screen, a stop, and the service going away.
 * No tap happens anywhere in this test.
 */
@RunWith(AndroidJUnit4::class)
class WidgetFollowsSessionTest : PlaybackHarness() {

    @Test
    fun everyPlaybackChangeReachesTheWidgetWithoutATap() {
        compose.runOnIdle { runtime.play() }
        val service = deliverStartCommand()
        await { runtime.status.value.playing }
        await { MixSurface.state(app) == MixState.PLAYING }

        // Home's own pause control.
        compose.runOnIdle { runtime.pause() }
        await { !runtime.status.value.playing }
        await { MixSurface.state(app) == MixState.PAUSED }

        // A media controller — the notification or the lock screen — resumes it.
        val session = sessionOf(service)
        compose.runOnIdle { session.player.play() }
        await { runtime.status.value.playing }
        await { MixSurface.state(app) == MixState.PLAYING }

        // "Stop all" resets the mix. That is stopped, not paused.
        compose.runOnIdle { runtime.stop() }
        await { MixSurface.state(app) == MixState.STOPPED }
        assertTrue("stop resets the mix", runtime.status.value.stopped)

        compose.runOnIdle { session.player.play() }
        await { runtime.status.value.playing }
        await { MixSurface.state(app) == MixState.PLAYING }

        // The service dies while playing: nothing is running afterwards, so neither is the widget.
        this.service?.destroy()
        this.service = null
        await { MixSurface.state(app) == MixState.STOPPED }
        assertEquals(
            "a released service leaves the widget stopped, whatever it last showed",
            MixState.STOPPED,
            MixSurface.state(app),
        )
    }
}
