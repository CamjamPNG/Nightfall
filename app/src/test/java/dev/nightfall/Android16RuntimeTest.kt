// SPDX-License-Identifier: MIT
package dev.nightfall

import android.graphics.Insets
import android.os.Build
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/**
 * The two behaviours targeting 36 turns on, measured on Android 16's own platform runtime instead of
 * on 35 with a 36 target.
 *
 * Every other test in this suite runs on the API 35 sandbox because Temurin 17 cannot start
 * Robolectric's API 36 one; CI's `api36` job runs these same tests on the 36 sandbox on Java 21
 * (`-PnightfallRobolectricSdk=36`). The assertions here need that runtime and are skipped without
 * it, so the 35 job reports them as skipped rather than passing them vacuously.
 *
 * What this can and cannot show: the app's own code running against the 36 platform classes — that
 * a committed predictive-back gesture reaches the activity, and that the window's insets are the
 * ones the screens lay themselves out with. No gesture, no system bar and no phone: whether a
 * physical Android 16 device reports the same values stays unverified (docs/TESTING.md).
 */
@RunWith(AndroidJUnit4::class)
class Android16RuntimeTest : PlaybackHarness() {

    /**
     * On API 36 back is reported through `OnBackInvokedDispatcher`; `onBackPressed` is not called and
     * `KeyEvent.KEYCODE_BACK` is not dispatched. So the assertion that matters is that the app has a
     * callback on the *window's* dispatcher — the object the platform invokes — and that invoking it
     * navigates, without ending the night.
     */
    @Test
    fun predictiveBackOnThePlatformDispatcherReturnsHomeAndKeepsPlayback() {
        requireApi36()

        clickTransport("Play soundscape")
        val playback = deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }

        open("Library")
        val callbacks = backCallbacks()
        println(
            "API ${Build.VERSION.SDK_INT} back callbacks ${callbacks.size} on " +
                compose.activity.onBackInvokedDispatcher.javaClass.name,
        )
        assertTrue(
            "the app registered nothing on the window's OnBackInvokedDispatcher at " +
                "PRIORITY_DEFAULT, so on API 36 a back gesture would never reach the activity",
            callbacks.isNotEmpty(),
        )
        compose.runOnIdle { callbacks.forEach { it.onBackInvoked() } }
        compose.waitForIdle()

        assertFalse(
            "back reported through OnBackInvokedDispatcher closed the screen instead of " +
                "returning to Home",
            compose.activity.isFinishing,
        )
        assertTrue(
            "back reported through OnBackInvokedDispatcher did not return to Home",
            onScreen("Nightfall"),
        )

        val written = FloatSinkAudioTrack.floatsWritten.get()
        // With no enabled app callback left, the close is the dispatcher's own fallback: on a phone
        // that is the system finishing the activity.
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue("back on Home did not close the screen", compose.activity.isFinishing)

        await { FloatSinkAudioTrack.floatsWritten.get() > written }
        assertTrue("closing the screen stopped playback", runtime.status.value.playing)
        assertFalse(
            "closing the screen stopped the foreground service",
            shadowOf(playback).isStoppedBySelf,
        )
        compose.runOnIdle { runtime.stop() }
    }

    /**
     * Android 16 drops the edge-to-edge opt-out for apps targeting 36, so the window's bars reach the
     * app as insets it has to lay itself out around. This measures the values the screens consume:
     * the header moves down by exactly the status bar's height and the navigation bar stays above the
     * gesture bar.
     */
    @Test
    fun theInsetsTheScreensConsumeAreTheWindowsOwnValues() {
        requireApi36()
        compose.waitUntil(WAIT_MS) { onScreen("Nightfall") }

        val statusBar = 132
        val gestureBar = 96
        val window = compose.activity.window.decorView
        val headerBefore = topOfHeader()
        val barBefore = bottomOfNavigationBar()

        window.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, gestureBar))
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, statusBar, 0, gestureBar))
                .build()
        )
        compose.waitForIdle()

        val headerAfter = topOfHeader()
        val barAfter = bottomOfNavigationBar()
        println(
            "API ${Build.VERSION.SDK_INT} INSETS header $headerBefore -> $headerAfter px, " +
                "navigation bar bottom $barBefore -> $barAfter px of ${window.height} px",
        )

        assertTrue(
            "the window reported no insets to the app: header at $headerAfter px, " +
                "navigation bar bottom at $barAfter px",
            headerAfter > headerBefore && barAfter < barBefore,
        )
        assertEquals(
            "the header did not move by the status bar's $statusBar px: the screen drew under it",
            headerBefore + statusBar,
            headerAfter,
            1f,
        )
        assertTrue(
            "the navigation bar ends at $barAfter px on a ${window.height} px window, past the " +
                "${window.height - gestureBar} px a $gestureBar px gesture bar leaves",
            barAfter <= window.height - gestureBar + 1,
        )
    }

    /** Fails the run on a sandbox that cannot show API 36 behaviour instead of passing it silently. */
    private fun requireApi36() =
        assumeTrue(
            "this asserts Android 16 behaviour and needs the API 36 sandbox " +
                "(-PnightfallRobolectricSdk=36 on Java 21); this run is API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT >= 36,
        )

    /**
     * The callbacks the app has registered on the window's `OnBackInvokedDispatcher` at
     * `PRIORITY_DEFAULT` — the priority AndroidX's `ComponentActivity` registers the bridge that
     * predictive back drives. From API 36 the window hands out a `ProxyOnBackInvokedDispatcher` that
     * forwards to the real dispatcher once the view attaches, which is where the registrations live
     * by the time a gesture can arrive. Both are hidden platform state, read reflectively here.
     */
    private fun backCallbacks(): List<OnBackInvokedCallback> {
        val window: OnBackInvokedDispatcher = compose.activity.onBackInvokedDispatcher
        val attached: OnBackInvokedDispatcher = checkNotNull(
            ReflectionHelpers.getField<OnBackInvokedDispatcher?>(window, "mActualDispatcher")
        ) { "the window's OnBackInvokedDispatcher (${window.javaClass.name}) is not attached yet" }
        val byPriority: Map<Int, List<OnBackInvokedCallback>> =
            ReflectionHelpers.getField(attached, "mOnBackInvokedCallbacks")
        return byPriority[OnBackInvokedDispatcher.PRIORITY_DEFAULT].orEmpty()
    }

    private fun topOfHeader(): Float =
        compose.onAllNodesWithText("Nightfall", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .minOf { it.boundsInRoot.top }

    private fun bottomOfNavigationBar(): Float =
        compose.onAllNodesWithText("Home", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.bottom }

    /** Opens a tab by its label and waits for the screen behind it. */
    private fun open(tab: String) {
        compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun onScreen(text: String) =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
