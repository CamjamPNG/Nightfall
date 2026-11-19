// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The two tap behaviours are two different intents, and nothing else: one opens the session through
 * this app's provider, the other opens the app and leaves playback alone.
 */
@RunWith(AndroidJUnit4::class)
class WidgetTapModeTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun noChoiceYetMeansPlayOrPauseHere() {
        assertEquals(WidgetTap.TOGGLE, PlaybackWidget.tap(app, WIDGET_ID))

        val saved = shadowOf(PlaybackWidget.tapIntent(app, WIDGET_ID)).savedIntent
        assertEquals(
            "the default tap must not open a window",
            PlaybackWidgetProvider::class.java.name,
            saved.component?.className,
        )
        assertEquals(PlaybackWidget.ACTION_TOGGLE, saved.action)
        assertEquals(WIDGET_ID, saved.getIntExtra(PlaybackWidget.EXTRA_APP_WIDGET_ID, -1))
    }

    @Test
    fun openNightfallLeavesPlaybackAlone() {
        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.OPEN_APP)

        val saved = shadowOf(PlaybackWidget.tapIntent(app, WIDGET_ID)).savedIntent
        assertEquals(MainActivity::class.java.name, saved.component?.className)
        assertTrue(
            "an activity launched from a widget needs its own task",
            saved.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
        )
        assertNull("open mode must not carry a playback command", saved.action)
    }

    @Test
    fun aWidgetThatOpensTheAppDoesNotAnnounceAPlaybackCommand() {
        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.OPEN_APP)
        val playing = PlaybackWidget.wideViews(app, WidgetState.PLAYING, WIDGET_ID)
        assertEquals(
            "a screen-reader user must hear what the tap does, not a pause that will not happen",
            "Open Nightfall. Playing.",
            playing.spoken(app),
        )
        // The state is still announced: this widget opens the app, it does not stop reporting audio.
        val stopped = PlaybackWidget.wideViews(app, WidgetState.STOPPED, WIDGET_ID)
        assertEquals("Open Nightfall. Stopped.", stopped.spoken(app))

        // And the default widget still says what a tap does to the audio.
        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.TOGGLE)
        assertEquals(
            "Pause soundscape. Playing.",
            PlaybackWidget.wideViews(app, WidgetState.PLAYING, WIDGET_ID).spoken(app),
        )
    }

    @Test
    fun theChoiceIsEditableAndDiesWithTheWidget() {
        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.OPEN_APP)
        assertEquals(WidgetTap.OPEN_APP, PlaybackWidget.tap(app, WIDGET_ID))
        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.TOGGLE)
        assertEquals("the launcher's Edit can put it back", WidgetTap.TOGGLE, PlaybackWidget.tap(app, WIDGET_ID))

        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.OPEN_APP)
        PlaybackWidget.forget(app, WIDGET_ID)
        assertEquals("a removed widget carries its choice away", WidgetTap.TOGGLE, PlaybackWidget.tap(app, WIDGET_ID))
    }

    private companion object {
        const val WIDGET_ID = 11
    }
}
