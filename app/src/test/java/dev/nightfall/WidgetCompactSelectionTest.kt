// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import org.junit.Before
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Before Android 12 there is no size-keyed `RemoteViews` map, so the widget has to read the size the
 * launcher last reported and pick the layout itself. Getting this wrong is not cosmetic: the wide
 * layout needs 84 dp for its padding, button and label margin alone, so a one-cell widget that got it
 * would have no room for the state at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class WidgetCompactSelectionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun placed() {
        // The shadow only tracks options for a widget id it knows about; a device's launcher has
        // already placed it by the time it sends the first update.
        org.robolectric.Shadows.shadowOf(AppWidgetManager.getInstance(app))
            .bindAppWidgetId(WIDGET_ID, ComponentName(app, PlaybackWidgetProvider::class.java))
    }

    @Test
    fun aOneCellWidgetOnAndroid11GetsTheCompactLayout() {
        options(width = 68, height = 68)
        assertEquals(
            R.layout.widget_nightfall_tiny,
            PlaybackWidget.views(app, WidgetState.PLAYING, WIDGET_ID).layoutId,
        )
    }

    @Test
    fun aTwoCellWidgetOnAndroid11GetsTheWideLayout() {
        options(width = 150, height = 70)
        assertEquals(
            R.layout.widget_nightfall,
            PlaybackWidget.views(app, WidgetState.PLAYING, WIDGET_ID).layoutId,
        )
    }

    @Test
    fun aResizeToTwoCellsSwapsTheLayout() {
        options(width = 68, height = 68)
        assertEquals(
            R.layout.widget_nightfall_tiny,
            PlaybackWidget.views(app, WidgetState.PLAYING, WIDGET_ID).layoutId,
        )
        options(width = 150, height = 70)
        assertEquals(
            R.layout.widget_nightfall,
            PlaybackWidget.views(app, WidgetState.PLAYING, WIDGET_ID).layoutId,
        )
    }

    /** What the launcher reports on add and on every resize, through the public manager API. */
    private fun options(width: Int, height: Int) = optionsFor(width, height)

    private fun optionsFor(width: Int, height: Int) =
        AppWidgetManager.getInstance(app)
            .updateAppWidgetOptions(
                WIDGET_ID,
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                },
            )

    private companion object {
        const val WIDGET_ID = 31
    }
}
