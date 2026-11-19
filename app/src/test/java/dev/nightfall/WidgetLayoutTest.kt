// SPDX-License-Identifier: MIT
package dev.nightfall

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Xml
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * The widget's declaration and the geometry of both layouts, measured rather than asserted.
 *
 * The sizes are the footprints this widget claims: one cell of a Pixel 7's launcher is about 68 dp,
 * so 68×68 and 150×70 stand in for 1×1 and 2×1. A launcher's own corner radius clips whatever sits
 * inside it, so every corner of the button is required to lie inside the surface's rounded rect.
 */
@RunWith(AndroidJUnit4::class)
class WidgetLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun theDeclarationSizesAndUpdatesSensibly() {
        val parser = context.resources.getXml(R.xml.widget_nightfall_info)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()
        val attrs = Xml.asAttributeSet(parser)

        assertEquals(
            "nothing polls: the service pushes every change",
            0,
            attrs.getAttributeIntValue(android, "updatePeriodMillis", -1),
        )
        assertEquals("one cell on the Android 12+ grid", 1, attrs.getAttributeIntValue(android, "targetCellWidth", -1))
        assertEquals(1, attrs.getAttributeIntValue(android, "targetCellHeight", -1))
        assertTrue(
            "a widget that cannot grow to 2x1 is not the same widget",
            dp(attrs, "maxResizeWidth") >= 150f,
        )
        assertTrue(dp(attrs, "maxResizeHeight") >= 60f)
        assertEquals("dev.nightfall.WidgetConfigureActivity", attrs.getAttributeValue(android, "configure"))
        assertEquals(
            "the launcher must offer the tap choice again after the widget is placed",
            AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE,
            attrs.getAttributeIntValue(android, "widgetFeatures", 0),
        )
        assertEquals(
            "resizeMode keeps both axes",
            AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
            attrs.getAttributeIntValue(android, "resizeMode", 0),
        )
        assertNotNull(
            "a widget with no description is not discoverable in the picker",
            context.resources.getString(
                attrs.getAttributeResourceValue(android, "description", 0)
            ),
        )
        // The preview is the wide layout, so the picker shows the state the widget was designed for.
        assertEquals(
            R.layout.widget_nightfall,
            attrs.getAttributeResourceValue(android, "previewLayout", 0),
        )
    }

    @Test
    fun theTinyLayoutFitsOneCell() {
        val root = measure(PlaybackWidget.tinyViews(context, WidgetState.PLAYING, 1), 68, 68)
        val button = root.findViewById<View>(R.id.widget_button)
        assertTrue("the 1x1 button must be a real tap target", button.width >= dp(32))
        assertTrue(
            "the 1x1 button is inside the surface's rounded rect",
            insideRoundedRect(button, root.width, root.height, dp(20)),
        )
        for (state in WidgetState.entries) {
            val views = measure(PlaybackWidget.tinyViews(context, state, 1), 68, 68)
            val label = views.findViewById<TextView>(R.id.widget_state)
            assertEquals(label.text.toString(), context.getString(labelFor(state)))
            assertEquals(
                "${label.text} is cut in the 1x1 layout",
                0,
                label.layout.getEllipsisCount(0),
            )
        }
    }

    @Test
    fun theWideLayoutKeepsTheButtonOffTheCorners() {
        val views = PlaybackWidget.wideViews(context, WidgetState.PAUSED, 1)
        val root = measure(views, 150, 70)
        val button = root.findViewById<View>(R.id.widget_button)
        assertTrue("the 2x1 button must be a real tap target", button.width >= dp(44))
        assertTrue(
            "the 2x1 button is inside the surface's rounded rect",
            insideRoundedRect(button, root.width, root.height, dp(20)),
        )
        val label = root.findViewById<TextView>(R.id.widget_state)
        assertEquals("Paused", label.text.toString())
        assertEquals("Paused is cut in the 2x1 layout", 0, label.layout.getEllipsisCount(0))
        assertTrue("the label sits beside the button", label.left > button.right)
    }

    /** Draws the layout at an exact size, the way a launcher's cell would. */
    private fun measure(views: android.widget.RemoteViews, widthDp: Int, heightDp: Int): View {
        val root = views.inflated(context)
        val width = dp(widthDp)
        val height = dp(heightDp)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        assertEquals("the layout did not take the size it was given", width, root.width)
        assertEquals(height, root.height)
        return root
    }

    /**
     * True when every corner of `view` lies inside a rectangle of `width`×`height` with corner
     * radius `radius` — the region our own surface drawable, or a launcher's rounding, leaves visible.
     */
    private fun insideRoundedRect(view: View, width: Int, height: Int, radius: Int): Boolean {
        val corners =
            listOf(
                view.left to view.top,
                view.right to view.top,
                view.left to view.bottom,
                view.right to view.bottom,
            )
        return corners.all { (x, y) ->
            val cx = x.coerceIn(radius, width - radius).toDouble()
            val cy = y.coerceIn(radius, height - radius).toDouble()
            val dx = x - cx
            val dy = y - cy
            dx * dx + dy * dy <= radius.toDouble() * radius + 0.001
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** A `125dp` declaration read back as a number: aapt writes it as `125.0dip`. */
    private fun dp(attrs: android.util.AttributeSet, name: String): Float =
        checkNotNull(attrs.getAttributeValue(android, name)) { "no $name in the widget declaration" }
            .removeSuffix("dip")
            .removeSuffix("dp")
            .removeSuffix("px")
            .toFloat()

    private fun labelFor(state: WidgetState): Int =
        when (state) {
            WidgetState.PLAYING -> R.string.widget_state_playing
            WidgetState.PAUSED -> R.string.widget_state_paused
            WidgetState.STOPPED -> R.string.widget_state_stopped
        }
}
