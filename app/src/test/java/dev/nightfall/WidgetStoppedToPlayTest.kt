// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.FloatSinkAudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stopped → playing, through the widget's own tap. The widget starts nothing itself: the tap opens
 * the app's media session, and the audio comes out of the same `AudioHost` the transport control
 * reaches.
 */
@RunWith(AndroidJUnit4::class)
class WidgetStoppedToPlayTest : PlaybackHarness() {

    @Test
    fun aTapOnAStoppedWidgetPlaysAndTheWidgetSaysSo() {
        // Nothing has started, so the widget reads the runtime rather than a remembered tap.
        assertEquals("a fresh install is stopped", MixState.STOPPED, MixSurface.state(app))
        assertTrue("the runtime agrees nothing is loaded", runtime.status.value.stopped)
        val stopped = PlaybackWidget.wideViews(app, MixState.STOPPED, WIDGET_ID)
        assertEquals("Stopped", stopped.stateLabel(app))
        assertEquals(R.drawable.widget_play, stopped.imageResourceId(R.id.widget_button))
        assertEquals("Play soundscape. Stopped.", stopped.spoken(app))

        bindSessionService()
        FloatSinkAudioTrack.reset()
        tapWidget()

        await { runtime.status.value.playing }
        awaitSamples()
        assertFalse("playing is not a stopped mix", runtime.status.value.stopped)
        assertEquals(MixState.PLAYING, MixSurface.state(app))

        // The picture the launcher would hold now, not the one it was handed a moment ago.
        val playing = PlaybackWidget.wideViews(app, MixSurface.state(app), WIDGET_ID)
        assertEquals("Playing", playing.stateLabel(app))
        assertEquals(R.drawable.widget_pause, playing.imageResourceId(R.id.widget_button))
        assertEquals("Pause soundscape. Playing.", playing.spoken(app))
        // The 1x1 variant carries the same state in the glyph and the spoken description.
        val tiny = PlaybackWidget.tinyViews(app, MixState.PLAYING, WIDGET_ID)
        assertEquals(R.drawable.widget_pause, tiny.imageResourceId(R.id.widget_button))
        assertEquals("Pause soundscape. Playing.", tiny.spoken(app))
    }

    private companion object {
        const val WIDGET_ID = 7
    }
}
