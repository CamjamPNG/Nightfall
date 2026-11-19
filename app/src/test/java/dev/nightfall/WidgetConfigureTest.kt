// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The screen the launcher shows when the widget is added, driven the way a person drives it: pick a
 * behaviour, press Done, and the widget is added with that choice recorded.
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigureTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun pickingOpenNightfallRecordsItAndAddsTheWidget() {
        val controller = Robolectric.buildActivity(WidgetConfigureActivity::class.java, configureIntent()).setup()
        compose.waitForIdle()

        compose.onNodeWithText(app.getString(R.string.widget_action_open)).performClick()
        compose.onNodeWithText(app.getString(R.string.common_done)).performClick()

        val activity = shadowOf(controller.get())
        assertEquals("the launcher only adds the widget on OK", android.app.Activity.RESULT_OK, activity.resultCode)
        assertEquals(
            WIDGET_ID,
            activity.resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
        )
        assertEquals(WidgetTap.OPEN_APP, PlaybackWidget.tap(app, WIDGET_ID))
    }

    @Test
    fun theScreenStartsOnTheBehaviourTheWidgetAlreadyHas() {
        PlaybackWidget.setTap(app, WIDGET_ID, WidgetTap.OPEN_APP)
        Robolectric.buildActivity(WidgetConfigureActivity::class.java, configureIntent()).setup()
        compose.waitForIdle()

        // The second visit is an edit, not a fresh choice: Done with nothing touched keeps it.
        compose.onNodeWithText(app.getString(R.string.common_done)).performClick()
        assertEquals(WidgetTap.OPEN_APP, PlaybackWidget.tap(app, WIDGET_ID))
    }

    @Test
    fun aConfigureScreenWithNoWidgetIdClosesWithoutAddingAnything() {
        val controller =
            Robolectric.buildActivity(WidgetConfigureActivity::class.java, Intent(app, WidgetConfigureActivity::class.java))
                .setup()
        assertEquals(
            "a configure screen with no widget to configure must not add one",
            android.app.Activity.RESULT_CANCELED,
            shadowOf(controller.get()).resultCode,
        )
        assertEquals(WidgetTap.TOGGLE, PlaybackWidget.tap(app, WIDGET_ID))
    }

    private fun configureIntent(): Intent =
        Intent(app, WidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID)

    private companion object {
        const val WIDGET_ID = 12
    }
}
