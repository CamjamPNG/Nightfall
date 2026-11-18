// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home screen, twice: the top of the mixer while it plays, and the layer stack under it.
 *
 * Rendered from `Presets.all.last()` — Nine-Layer Dream, the preset the playback tests restore — so
 * the title, the "9 layers" count and every row's own name and gain are read out of the model the
 * audio thread is mixing at that moment.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class MixerScreensRenderTest : ScreenshotHarness() {

    @Test
    fun homePlaysTheNineLayerMixAndShowsItsLayerStack() {
        startPlayback()
        compose.waitUntil(WAIT_MS) { onScreen("9 layers") }
        val layers = runtime.saved.value.current.layers
        assertEquals(9, layers.size)
        assertTrue("the mix's own name is not on screen", onScreen("Nine-Layer Dream"))
        assertTrue("the transport did not report playback", onScreen("NOW PLAYING"))

        val top = capture("01-home-playing.png")
        assertEquals("411 dp at 420 dpi", 1078, top.width)
        assertEquals("914 dp at 420 dpi", 2399, top.height)

        scrollScreenTo(5)
        val first = layers.first().name
        assertTrue("layer '$first' is not on screen", onScreen(first))
        assertTrue("no layer volume control is on screen", onScreen("Volume"))
        capture("02-home-layers.png")
    }
}
