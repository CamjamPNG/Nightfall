// SPDX-License-Identifier: MIT
package dev.nightfall.render

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import dev.nightfall.PlaybackHarness
import java.io.File

/**
 * Writes PNGs of the app's own window for a listing: the shipped `NightfallScreen` behind the real
 * `MainActivity`, on a JVM, with no emulator and nothing mocked up.
 *
 * Each screen is reached through the app's own controls — a navigation tab, a layer's options button
 * — and every shot is checked for the two ways a render fails quietly: a frame that did not draw
 * (too few colours) and a frame of a different screen (two identical pictures). Tests are one
 * scenario per class, because the repository's Robolectric suite requires one JVM per test class.
 *
 * One PNG per scenario keeps the state honest: the measure is drawn, then the list is scrolled, then
 * drawn again, in the same session.
 *
 * What a still frame cannot show: animation (the playing waveform is a single phase, and Reduced
 * motion is on so that phase is not a race), anything scrolled out of the 411×914 dp frame, and the
 * audio meters, which read zero in a few places because Robolectric's `AudioTrack` accepts samples
 * and plays nothing. The status bar and gesture bar are absent: this is the app's window, not a
 * phone's screen stack.
 */
abstract class ScreenshotHarness : PlaybackHarness() {

    /** 411×914 dp at 420 dpi: a Pixel 7's logical size, so a listing shows what a phone shows. */
    protected val out = File("build/screenshots/github")

    /** Every shot this class wrote, in order, for the duplicate check. */
    private val shots = mutableListOf<Shot>()

    /**
     * Draws the activity's window as it stands and writes it as a PNG.
     *
     * `decorView.draw` is the whole mechanism: under Robolectric's native graphics the view tree is
     * drawn by a real Skia canvas, which is why the pixels are the app's own. Compose's
     * `captureToImage` cannot be used here; it waits for a hardware redraw callback that Robolectric
     * never sends.
     */
    protected fun capture(name: String): Shot {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        out.mkdirs()
        val file = File(out, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val shot = Shot(file, bitmap.width, bitmap.height, pixels.toHashSet().size, pixels)
        check(file.length() > 20_000) { "${file.name} is only ${file.length()} bytes" }
        check(shot.colors > 400) { "${file.name} has ${shot.colors} colours: it did not draw" }
        for (earlier in shots)
            check(earlier.differsFrom(shot)) {
                "${file.name} is the same picture as ${earlier.file.name}: one of them did not draw"
            }
        shots += shot
        println(
            "RENDER ${file.name} ${shot.width}x${shot.height} " +
                "${file.length()} bytes ${shot.colors} colours"
        )
        return shot
    }

    /** Opens a tab the way a person does, then waits for its heading to be placed. */
    protected fun open(tab: String, heading: String) {
        compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText(heading, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.layoutInfo.isPlaced }
        }
    }

    /**
     * Starts playback for real: the transport control asks the runtime, the runtime asks Android, and
     * `deliverStartCommand` plays the part of the OS, so "NOW PLAYING" is the audio thread's own state
     * rather than a flag this test set.
     */
    protected fun startPlayback() {
        compose.runOnIdle {
            runtime.change { it.copy(settings = it.settings.copy(reducedMotion = true)) }
        }
        clickTransport("Play soundscape")
        deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }
        // The card reads playback through a lifecycle-aware flow that does not always recompose on a
        // flip (the limit `docs/TESTING.md` records), so re-enter the tab the way a person would: the
        // second entry reads the state the audio thread already holds.
        open("Library", "Sound library")
        open("Home", "Your soundscape")
        awaitTransport("Pause soundscape")
    }

    protected fun onScreen(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    /**
     * The screen's own list is the tallest scrollable in the tree. It used to be simply the first
     * one, until the Library put a horizontal chip rail above its list: that rail is a scrollable
     * too, and scrolling it by index scrolls the wrong thing.
     */
    protected fun screenList(): SemanticsNodeInteraction {
        val scrollables = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        val tallest = scrollables.indices.maxBy { scrollables[it].boundsInRoot.height }
        return compose.onAllNodes(hasScrollAction())[tallest]
    }

    protected fun scrollScreenTo(index: Int) {
        screenList().performScrollToIndex(index)
        compose.waitForIdle()
    }

    /** Taps the options button of the first layer that offers one. */
    protected fun openLayerOptions() {
        compose.onAllNodesWithContentDescription("Options for", substring = true, useUnmergedTree = true)[0]
            .performClick()
    }

    class Shot(
        val file: File,
        val width: Int,
        val height: Int,
        val colors: Int,
        private val pixels: IntArray,
    ) {
        fun differsFrom(other: Shot) =
            width != other.width ||
                height != other.height ||
                !pixels.contentEquals(other.pixels)
    }
}
