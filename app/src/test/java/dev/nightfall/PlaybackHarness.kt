// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import dev.nightfall.core.Presets
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Robolectric stand-in for the parts of Android the real app runs against.
 *
 * Real here: NightfallApp, NightfallRuntime, the Compose screens, PlaybackService, AudioHost, its
 * PCM worker thread and the core PcmMixer. Shadowed: AudioManager (it records the focus request but
 * never arbitrates between apps), AudioTrack ([FloatSinkAudioTrack] accepts and records writes but
 * plays nothing), PowerManager, NotificationManager and ActivityManager. Robolectric never starts a
 * service on its own, so [deliverStartCommand] plays the part of the OS: it takes the Intent the app
 * handed to startForegroundService and drives a real PlaybackService with it.
 */
@Config(shadows = [FloatSinkAudioTrack::class])
abstract class PlaybackHarness {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    protected val app: NightfallApp
        get() = ApplicationProvider.getApplicationContext<Application>() as NightfallApp

    protected val runtime: NightfallRuntime
        get() = app.runtime

    protected var service: ServiceController<PlaybackService>? = null

    @Before
    fun awaitRestoredAndLoadNineLayerMix() {
        FloatSinkAudioTrack.reset()
        await { runtime.ready.value }
        compose.runOnIdle { runtime.mix { Presets.all.last() } }
        assertEquals("the nine-layer preset must be the loaded mix", 9, runtime.saved.value.current.layers.size)
    }

    @After
    fun releaseEverything() {
        compose.runOnIdle { runtime.stop() }
        service?.destroy()
        service = null
    }

    /**
     * Bounded poll. AudioHost publishes its meters from a 250 ms main-dispatcher tick and DataStore
     * writes state after a debounce, so every poll drains the main looper and lets virtual time pass
     * instead of only looking at the condition.
     */
    protected fun await(condition: () -> Boolean) =
        compose.waitUntil(WAIT_MS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            condition()
        }

    /** Waits until the audio thread has written more samples than it had. */
    protected fun awaitSamples() {
        val before = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > before }
    }

    /** Real time on purpose: measures how many samples the audio thread writes in a window. */
    protected fun samplesDuring(ms: Long): Long {
        val before = FloatSinkAudioTrack.floatsWritten.get()
        Thread.sleep(ms)
        return FloatSinkAudioTrack.floatsWritten.get() - before
    }

    /** Advances Robolectric's virtual main clock (and with it SystemClock) without real waiting. */
    protected fun advanceClock(ms: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Acts as ActivityManager: starts PlaybackService with the Intent the app asked for. */
    protected fun deliverStartCommand(): PlaybackService {
        val intent: Intent =
            checkNotNull(shadowOf(app).nextStartedService) {
                "the app never called startForegroundService"
            }
        assertEquals(PlaybackService::class.java.name, intent.component?.className)
        assertEquals("play", intent.action)
        val controller =
            Robolectric.buildService(PlaybackService::class.java, intent).create().startCommand(0, 1)
        service = controller
        return controller.get()
    }

    /** The media session a controller would connect to, as the service publishes it. */
    protected fun sessionOf(service: PlaybackService): MediaSession =
        checkNotNull(
            service.onGetSession(
                MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                    app.packageName,
                    Process.myPid(),
                    Process.myUid(),
                    /* libraryVersion = */ 0,
                    /* interfaceVersion = */ 0,
                    /* trusted = */ true,
                    Bundle(),
                )
            )
        ) { "PlaybackService answered no media session" }

    /** Waits until exactly one placed node carries this content description. */
    protected fun awaitTransport(label: String) =
        compose.waitUntil(WAIT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().count {
                it.layoutInfo.isPlaced
            } == 1
        }

    /** Clicks a control by its content description, waiting for it to be placed first. */
    protected fun clickTransport(label: String) {
        awaitTransport(label)
        compose.onNodeWithContentDescription(label).performClick()
    }

    companion object {
        const val WAIT_MS = 30_000L
    }
}
