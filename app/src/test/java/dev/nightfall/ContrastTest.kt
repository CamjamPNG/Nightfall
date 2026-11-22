// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contrast Nightfall actually ships, computed rather than looked at.
 *
 * WCAG 2.2 asks for 4.5:1 on body text and 3:1 on the parts that identify a control — its boundary,
 * its fill, its icon. A night theme is allowed to be dim, so the interesting question is not "is it
 * dark" but "is the darkest thing that has to be seen still above the line". This test reads the
 * palette the theme is built from, so a later "just a bit softer" edit fails here instead of in a
 * stranger's dark bedroom.
 *
 * The margin is deliberate: every pair must clear its requirement by at least [MARGIN], so a colour
 * that lands one hundredth above the line does not pass on luck.
 */
class ContrastTest {

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance, WCAG 2.2 §relative luminance. */
    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = max(luminance(a), luminance(b)) to min(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(what: String, foreground: Color, background: Color, required: Double) {
        val measured = ratio(foreground, background)
        assertTrue(
            "%s is %.2f:1, and %.1f:1 + %.1f margin is the floor".format(what, measured, required, MARGIN),
            measured >= required + MARGIN,
        )
    }

    @Test
    fun bodyTextClearsFourPointFiveInBothThemes() {
        // Night: the two surfaces text is drawn on, and the two greys it is drawn in.
        assertContrast("White on the night background", White, Navy, TEXT)
        assertContrast("White on a night card", White, CardNavy, TEXT)
        assertContrast("Muted on the night background", Muted, Navy, TEXT)
        assertContrast("Muted on a night card", Muted, CardNavy, TEXT)
        assertContrast("Lavender on the night background", Lavender, Navy, TEXT)
        assertContrast("Active on the night background", Active, Navy, TEXT)
        // Day.
        assertContrast("Text on the day background", DayOnSurface, DayBackground, TEXT)
        assertContrast("Text on a day card", DayOnSurface, DaySurface, TEXT)
        assertContrast("Secondary text on the day background", DayOnSurfaceVariant, DayBackground, TEXT)
        assertContrast("Secondary text on a day card", DayOnSurfaceVariant, DaySurface, TEXT)
        assertContrast("Primary on the day background", DayPrimary, DayBackground, TEXT)
        assertContrast("Primary on a day card", DayPrimary, DaySurface, TEXT)
    }

    @Test
    fun controlBoundariesClearThreeInBothThemes() {
        // The slider's inactive groove is the one boundary the night theme used to lose: at #30384C
        // it measured 1.62:1 on the background and 1.40:1 on a card.
        assertContrast("The night slider groove on the background", Track, Navy, CONTROL)
        assertContrast("The night slider groove on a card", Track, CardNavy, CONTROL)
        // The day theme's groove, which used to be the outline at 30% alpha: 1.41:1 and 1.37:1.
        assertContrast("The day slider groove on the background", DayOutline, DayBackground, CONTROL)
        assertContrast("The day slider groove on a day card", DayOutline, DaySurface, CONTROL)
    }

    @Test
    fun theWidgetClearsTheSameLinesInBothPalettes() {
        // Two palettes, declared as resources because a launcher inflates the widget in its own
        // process: dark uses CardNavy/White/Lavender, light uses the day surface and ink.
        assertContrast("The dark widget's label", White, CardNavy, TEXT)
        assertContrast("The dark widget's glyph on its button", Navy, Lavender, CONTROL)
        assertContrast("The light widget's label", DayOnSurface, DayBackground, TEXT)
        assertContrast("The light widget's glyph on its button", Color.White, DayPrimary, CONTROL)
    }

    private companion object {
        const val TEXT = 4.5
        const val CONTROL = 3.0
        const val MARGIN = 0.4
    }
}
