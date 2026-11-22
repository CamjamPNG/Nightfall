// SPDX-License-Identifier: MIT
package dev.nightfall.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.PlaybackWidget
import dev.nightfall.MixState
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * PNGs of the widget itself: the real layouts, inflated from `RemoteViews` and drawn by a real Skia
 * canvas at the launcher's density, at both footprints and both theme variants.
 *
 * This is the closest this machine gets to a home screen. It cannot show a launcher's own corner
 * radius, its cell padding, or a live wallpaper behind the widget — those belong to a device
 * (CAM-7).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderTest {
    private val out = File("build/screenshots/github")

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theWidgetDrawsAtOneCellAndTwoCellsInBothThemes() {
        val shots = mutableMapOf<String, IntArray>()
        for (night in listOf(false, true)) {
            // xxhdpi is a Pixel 7's density; the theme comes from the launcher's night mode, which
            // is absent (day) rather than named in the light variant.
            RuntimeEnvironment.setQualifiers(if (night) "night-xxhdpi" else "xxhdpi")
            val theme = if (night) "dark" else "light"
            for ((cells, views) in variants()) {
                val (width, height) = if (cells == "1x1") 68 to 68 else 150 to 70
                val name = "widget-$cells-$theme.png"
                shots[name] = shot(name, views, width, height)
            }
        }
        // A frame that did not draw, or a theme that never reached the resources, shows up here.
        val light = shots.getValue("widget-2x1-light.png")
        val dark = shots.getValue("widget-2x1-dark.png")
        assertTrue("the light widget did not draw", light.size > 400)
        assertTrue("the dark widget did not draw", dark.size > 400)
        assertTrue("light and dark are the same picture", !light.contentEquals(dark))
    }

    /** Both footprints, in the state that shows the most: playing, then a stopped wide widget. */
    private fun variants(): List<Pair<String, android.widget.RemoteViews>> {
        val context = context
        return listOf(
            "1x1" to PlaybackWidget.tinyViews(context, MixState.PLAYING, WIDGET_ID),
            "2x1" to PlaybackWidget.wideViews(context, MixState.PLAYING, WIDGET_ID),
            "2x1-stopped" to PlaybackWidget.wideViews(context, MixState.STOPPED, WIDGET_ID),
        )
    }

    /** Draws one widget over a launcher-grey backdrop so its own surface and corners are visible. */
    private fun shot(
        name: String,
        views: android.widget.RemoteViews,
        widthDp: Int,
        heightDp: Int,
    ): IntArray {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val pad = (8 * density).toInt()

        val root: View = views.apply(context, FrameLayout(context))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width + pad * 2, height + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF54606E.toInt())
        canvas.translate(pad.toFloat(), pad.toFloat())
        root.draw(canvas)

        out.mkdirs()
        val file = File(out, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val colours = pixels.toHashSet()
        assertTrue("${file.name} is only ${file.length()} bytes", file.length() > 1_000)
        println("RENDER ${file.name} ${bitmap.width}x${bitmap.height} ${file.length()} bytes ${colours.size} colours")
        return colours.toIntArray()
    }

    private companion object {
        const val WIDGET_ID = 21
    }
}
