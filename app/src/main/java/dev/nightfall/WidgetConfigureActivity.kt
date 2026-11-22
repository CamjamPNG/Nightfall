// SPDX-License-Identifier: MIT
package dev.nightfall

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The widget's one setting, asked where the widget is chosen rather than buried in app settings: what
 * a tap does. The launcher runs this when the widget is added, and again from its widget options
 * afterwards — that second visit is why the current choice is read back rather than assumed.
 *
 * The screen follows the launcher's dark mode, the same qualifier the widget itself is drawn with, so
 * it cannot arrive light beside a dark widget.
 */
class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var chosen by mutableStateOf(WidgetTap.TOGGLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A configure activity that never answers is a cancellation: the launcher then adds nothing.
        setResult(RESULT_CANCELED)
        appWidgetId =
            intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        chosen = PlaybackWidget.tap(this, appWidgetId)
        enableEdgeToEdge()
        val dark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        setContent {
            NightfallTheme(light = !dark) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        Modifier.fillMaxSize()
                            .systemBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(22.dp)
                    ) {
                        Text(
                            stringResource(R.string.widget_configure_title),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.widget_configure_prompt),
                            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Choice(
                            WidgetTap.TOGGLE,
                            stringResource(R.string.widget_configure_toggle),
                            stringResource(R.string.widget_configure_toggle_note),
                        )
                        Choice(
                            WidgetTap.OPEN_APP,
                            stringResource(R.string.widget_action_open),
                            stringResource(R.string.widget_configure_open_note),
                        )
                        Text(
                            stringResource(R.string.widget_configure_note),
                            modifier = Modifier.padding(top = 18.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 22.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            TextButton(onClick = ::save) {
                                Text(stringResource(R.string.common_done))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Choice(tap: WidgetTap, title: String, note: String) {
        val selected = chosen == tap
        Row(
            Modifier.fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.background
                )
                // One node, not two. A bare clickable row plus a radio button beside it is two
                // focus stops in the same place: the button announces "not selected" and the row
                // announces the words, and neither is the control. Selecting on the row merges its
                // title and note into the thing that is actually pressed.
                .selectable(selected = selected, role = Role.RadioButton) { chosen = tap }
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 4.dp)) {
                Text(title, fontSize = 15.sp)
                Text(
                    note,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    private fun save() {
        PlaybackWidget.setTap(this, appWidgetId, chosen)
        PlaybackWidget.refresh(this)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }
}
