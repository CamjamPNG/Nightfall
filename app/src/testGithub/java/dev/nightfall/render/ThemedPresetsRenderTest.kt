// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A themed night in the Presets tab: the built-in card, the layer count, and the one line that says
 * what the night does rather than how it feels.
 *
 * The line is asserted on screen before the frame is drawn, because a still frame proves that
 * something was drawn and not which words are in it. The list is scrolled the way a person scrolls it,
 * to the theme cards, which sit past the twenty-one presets that came before them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class ThemedPresetsRenderTest : ScreenshotHarness() {

    @Test
    fun aThemedNightSaysWhatItDoes() {
        open("Presets", "Find your night")
        // Heading, the import row, the built-in collection's label, then one row per preset.
        scrollScreenTo(22)
        assertTrue("the themed card is not on screen", onScreen("Storm That Passes"))
        assertTrue(
            "the themed card does not say what the night does",
            onScreen("drizzle by four", substring = true),
        )
        capture("05b-presets-themed.png")
    }
}
