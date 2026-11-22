// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Paused → playing: the second tap resumes the loaded mix rather than starting a second one. */
@RunWith(AndroidJUnit4::class)
class WidgetPausedToPlayTest : PlaybackHarness() {

    @Test
    fun aTapOnAPausedWidgetResumesTheLoadedMix() {
        compose.runOnIdle { runtime.play() }
        deliverStartCommand()
        await { runtime.status.value.playing }
        awaitSamples()
        compose.runOnIdle { runtime.pause() }
        await { !runtime.status.value.playing }
        await { MixSurface.state(app) == MixState.PAUSED }
        val layers = runtime.saved.value.current.layers.size

        FloatSinkAudioTrack.reset()
        tapWidget()

        await { runtime.status.value.playing }
        awaitSamples()
        assertEquals(MixState.PLAYING, MixSurface.state(app))
        assertFalse("a resume is not a stop", runtime.status.value.stopped)
        assertEquals(
            "the resume must play the mix that was loaded, not a new one",
            layers,
            runtime.saved.value.current.layers.size,
        )
        val playing = PlaybackWidget.wideViews(app, MixState.PLAYING, WIDGET_ID)
        assertEquals("Playing", playing.stateLabel(app))
        assertTrue(MixSurface.state(app) == MixState.PLAYING)
    }

    private companion object {
        const val WIDGET_ID = 9
    }
}
