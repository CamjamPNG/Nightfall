// SPDX-License-Identifier: MIT
package dev.nightfall

import android.media.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nightfall.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val runtime
        get() =
            (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    as NightfallApp)
                .runtime

    @Before
    fun setup() {
        compose.waitUntil(45000) { runtime.ready.value }
        compose.runOnIdle {
            runtime.stop()
            runtime.mix { Presets.all.last() }
        }
    }

    @After
    fun cleanup() {
        compose.runOnIdle { runtime.stop() }
    }

    @Test
    fun nineLayerPlaybackSurvivesNavigationAndActivityRecreation() {
        compose.onNodeWithContentDescription("Play soundscape").performClick()
        compose.waitUntil(45000) { runtime.status.value.playing && runtime.status.value.peak > 0f }
        assertEquals(9, runtime.saved.value.current.layers.size)
        compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Sound library").assertIsDisplayed()
        assertTrue(runtime.status.value.playing)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30000) { runtime.status.value.playing }
        assertEquals(9, runtime.saved.value.current.layers.size)
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Pause soundscape").assertIsDisplayed()
    }

    @Test
    fun focusInterruptionPausesAndGainResumes() {
        compose.runOnIdle { runtime.play() }
        compose.waitUntil(45000) { runtime.status.value.playing }
        val manager =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getSystemService(AudioManager::class.java)
        val focus =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
                .setOnAudioFocusChangeListener({})
                .build()
        compose.runOnIdle {
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(focus))
        }
        compose.waitUntil(30000) { !runtime.status.value.playing }
        compose.runOnIdle { manager.abandonAudioFocusRequest(focus) }
        compose.waitUntil(30000) { runtime.status.value.playing }
    }

    @Test
    fun settingsAndMixerStateRestoreFromLocalStorage() {
        compose.runOnIdle {
            runtime.layer(runtime.saved.value.current.layers.first().id) {
                it.copy(gain = .27f, pan = -.2f)
            }
            runtime.change { it.copy(settings = it.settings.copy(reducedMotion = true)) }
        }
        compose.waitUntil(30000) { runtime.saved.value.current.layers.first().gain == .27f }
        compose.activityRule.scenario.recreate()
        assertEquals(.27f, runtime.saved.value.current.layers.first().gain, 0f)
        assertTrue(runtime.saved.value.settings.reducedMotion)
    }

    @Test
    fun backgroundPlaybackAndTimerExpiration() {
        compose.runOnIdle { runtime.play() }
        compose.waitUntil(30000) { runtime.status.value.playing && runtime.status.value.peak > 0f }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertTrue(runtime.status.value.playing)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            runtime.timer.start(android.os.SystemClock.elapsedRealtime(), 1500, 500)
        }
        compose.waitUntil(30000) { !runtime.status.value.playing }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    }
}
