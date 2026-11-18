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
 * The screens on a wide window instead of a tall one.
 *
 * Android 16 stops honouring `screenOrientation`, `resizableActivity` and the aspect-ratio attributes
 * for apps targeting 36 on any display at least 600 dp wide, so a tablet or unfolded foldable can
 * give this app a 914×411 dp window. The app locks no orientation and caps its content at 720 dp, so
 * the question the change raises is only whether the screens survive a wide, short window: this draws
 * them and reads the layout back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w914dp-h411dp-420dpi")
class WideScreenRenderTest : ScreenshotHarness() {

    @Test
    fun theMixerAndTheLibrarySurviveAWideShortWindow() {
        compose.waitUntil(WAIT_MS) { onScreen("Nightfall") }
        assertTrue("the header is missing in a wide window", onScreen("Nightfall"))
        val home = capture("08-home-wide.png")
        assertEquals("914 dp at 420 dpi", 2399, home.width)
        assertEquals("411 dp at 420 dpi", 1078, home.height)

        open("Library", "Sound library")
        val library = capture("09-library-wide.png")
        assertEquals(2399, library.width)
        assertEquals(1078, library.height)

        // A 411 dp tall window shows the heading, the search field and the category chips before the
        // first sound: the catalogue rows have to be reachable, not merely exist below the fold.
        scrollScreenTo(2)
        assertTrue(
            "no catalogue row is reachable in a wide, short window",
            onScreen("White") || onScreen("Pink") || onScreen("Brown"),
        )
    }
}
