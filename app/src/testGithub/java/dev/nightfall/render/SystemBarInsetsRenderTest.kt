// SPDX-License-Identifier: MIT
package dev.nightfall.render

import android.graphics.Insets
import android.view.WindowInsets
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The other half of the six screenshots: what the app looks like when a phone's own bars are there.
 *
 * Android 16 removes the edge-to-edge opt-out for apps targeting 36, so the window fills the display
 * and the app has to keep its own chrome out from under the status bar and the gesture bar itself.
 * Robolectric's window has no bars and reports zero insets, which is why every other frame is drawn
 * where a phone that shows its bars would not draw it. This test hands the window the insets such a
 * phone delivers and measures that the header and the navigation bar move by exactly that much: the
 * same screen, the same code, with the bars accounted for.
 *
 * Dispatched insets, not a real phone: the platform is not deciding anything here, the app's own
 * `Scaffold` and `NavigationBar` inset handling is, and that is what the Android 16 change puts on
 * the app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class SystemBarInsetsRenderTest : ScreenshotHarness() {

    private fun topOfHeader(): Float =
        compose.onAllNodesWithText("Nightfall", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .minOf { it.boundsInRoot.top }

    private fun bottomOfNavigationBar(): Float =
        compose.onAllNodesWithText("Home", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.bottom }

    @Test
    fun aStatusBarAndAGestureBarPushTheAppsOwnChromeIn() {
        compose.waitUntil(WAIT_MS) { onScreen("Nightfall") }

        // 100 px each: a 38 dp status bar and gesture bar, chosen so a shift is unambiguous.
        val statusBar = 100
        val gestureBar = 100
        val window = compose.activity.window.decorView

        val headerBefore = topOfHeader()
        val barBefore = bottomOfNavigationBar()

        window.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, gestureBar))
                .setInsets(
                    WindowInsets.Type.systemBars(),
                    Insets.of(0, statusBar, 0, gestureBar),
                )
                .build()
        )
        compose.waitForIdle()

        val headerAfter = topOfHeader()
        val barAfter = bottomOfNavigationBar()
        println(
            "INSETS header $headerBefore -> $headerAfter px, " +
                "navigation bar bottom $barBefore -> $barAfter px of ${window.height} px"
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

        capture("07-home-with-system-bars.png")
    }
}
