// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import dev.nightfall.core.PresetJson
import dev.nightfall.core.Presets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
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

    /** The tile the shade would be listening through, when a test has opened one. */
    protected var tile: ServiceController<NightfallTileService>? = null

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
        // The tile leaves listening state rather than being destroyed: Robolectric's TileService
        // shadow does not extend ShadowService, so `Service.onDestroy` cannot be dispatched through
        // it, and nothing this suite asserts depends on that lifecycle call.
        tile?.get()?.onStopListening()
        tile = null
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

    /**
     * Waits until the state on disk is the state the runtime holds, so an assertion about saving
     * follows a finished write instead of racing a debounced one. The Preferences file is a
     * protobuf whose one string value is the saved state's JSON, so the exact document the runtime
     * would write is what the file has to contain.
     */
    protected fun awaitSaved() = await {
        val file = app.filesDir.walkTopDown().firstOrNull { it.name == "nightfall.preferences_pb" }
        file != null &&
            String(file.readBytes(), Charsets.ISO_8859_1)
                .contains(PresetJson.json.encodeToString(runtime.saved.value))
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

    /**
     * The `PlaybackService` a widget tap would reach. On a device the tap's controller bind creates
     * the service; Robolectric never starts a service by binding, so the test creates it and
     * [tapWidget] hands its binder to the bind.
     */
    protected fun bindSessionService(): PlaybackService {
        val controller =
            Robolectric.buildService(
                    PlaybackService::class.java,
                    Intent(app, PlaybackService::class.java).setAction("play"),
                )
                .create()
        service = controller
        return controller.get()
    }

    /**
     * Points the app's bind at the service this test already created, which is where the OS would
     * reach a running one, so [SessionToggle] connects a real controller through that binder.
     */
    protected fun bindAppSession() {
        val name = ComponentName(app, PlaybackService::class.java)
        val binder =
            checkNotNull(
                service?.get()?.onBind(Intent(MediaSessionService.SERVICE_INTERFACE).setComponent(name))
            ) { "the bind needs a running PlaybackService" }
        shadowOf(app).setComponentNameAndServiceForBindService(name, binder)
    }

    /**
     * Taps the widget: the provider's handler, from opening the app's session to the command. The
     * app's own [SessionToggle] connects a real controller through the binder [bindAppSession] set.
     */
    protected fun tapWidget() {
        bindAppSession()
        val done = AtomicBoolean(false)
        SessionToggle.toggle(app) { done.set(true) }
        await { done.get() }
    }

    /**
     * Opens the shade on the tile: the listening state the system puts it in whenever the tile can
     * be seen, which is when it repaints from the record the service last published.
     */
    protected fun bindTile(): NightfallTileService {
        val controller = Robolectric.buildService(NightfallTileService::class.java).create()
        controller.get().onStartListening()
        tile = controller
        return controller.get()
    }

    /** Taps the tile, through the same session opener the widget's tap uses. */
    protected fun tapTile() {
        val listening = checkNotNull(tile) { "tapTile needs a tile the shade is listening to" }
        bindAppSession()
        listening.get().onClick()
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
