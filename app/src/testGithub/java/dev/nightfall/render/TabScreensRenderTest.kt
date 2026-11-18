// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three browsing tabs, reached by tapping the navigation bar: the sound library, the presets, and
 * Settings.
 *
 * Settings is the one screen that prints measured state instead of copy, so this asserts the numbers
 * on it against the runtime before the frame is drawn: the render load it shows is the value
 * `AudioHost` publishes for the audio thread.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class TabScreensRenderTest : ScreenshotHarness() {

    @Test
    fun libraryPresetsAndSettingsDraw() {
        open("Library", "Sound library")
        assertTrue("the catalogue did not list", onScreen("available", substring = true))
        capture("04-library.png")

        open("Presets", "Find your night")
        assertTrue("the built-in collection did not list", onScreen("Nightfall collection"))
        capture("05-presets.png")

        open("Settings", "Make yourself at home")
        scrollScreenTo(11)
        val load = (runtime.status.value.load * 100).roundToInt()
        assertTrue(
            "the device readout does not match the runtime",
            onScreen("Audio render load: $load% of buffer time", substring = true),
        )
        scrollScreenTo(0)
        assertTrue("the settings heading left the screen", onScreen("Make yourself at home"))
        capture("06-settings.png")
    }
}
