// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One layer with its options panel open, as a person opens it: scroll to the row, tap its own
 * options button, and let the panel report the layer's current placement, tone and filters. The
 * sliders' values and their "adjusted" count come from the layer the mixer holds, not from a form.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class LayerOptionsRenderTest : ScreenshotHarness() {

    @Test
    fun aLayerShowsItsExpandedOptions() {
        startPlayback()
        scrollScreenTo(5)
        val first = runtime.saved.value.current.layers.first().name
        assertTrue("the first layer's options button is not on screen", onScreen("Options"))
        openLayerOptions()
        compose.waitUntil(WAIT_MS) { onScreen("Placement") }
        assertTrue("the tone section did not open", onScreen("Spectral tilt"))
        assertTrue("the filter section did not open", onScreen("High-pass filter"))
        scrollScreenTo(5)
        capture("03-layer-options.png")
    }
}
