// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Playing → paused, through the widget's tap, measured twice: the status the session reports and a
 * window in which the audio thread must write nothing. A widget that drew "Paused" over running
 * audio would pass on the label alone.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPlayingToPauseTest : PlaybackHarness() {

    @Test
    fun aTapWhilePlayingPausesAndTheAudioStops() {
        compose.runOnIdle { runtime.play() }
        deliverStartCommand()
        await { runtime.status.value.playing }
        awaitSamples()
        await { MixSurface.state(app) == MixState.PLAYING }

        tapWidget()

        await { !runtime.status.value.playing }
        assertFalse("a pause is not a stop", runtime.status.value.stopped)
        assertEquals(MixState.PAUSED, MixSurface.state(app))
        val paused = PlaybackWidget.wideViews(app, MixState.PAUSED, WIDGET_ID)
        assertEquals("Paused", paused.stateLabel(app))
        assertEquals(R.drawable.widget_play, paused.imageResourceId(R.id.widget_button))

        // Let the block already in flight land, then measure a window that must stay silent.
        Thread.sleep(100)
        assertEquals(
            "the audio thread kept writing after the widget paused it",
            0L,
            samplesDuring(250),
        )
        // And the widget is still telling the truth a quarter of a second later.
        assertEquals(MixState.PAUSED, MixSurface.state(app))
        assertTrue(MixSurface.state(app) != MixState.PLAYING)
    }

    private companion object {
        const val WIDGET_ID = 8
    }
}
