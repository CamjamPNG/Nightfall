// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nightfall.NightfallRuntime
import dev.nightfall.R
import dev.nightfall.SectionLabel
import dev.nightfall.SettingToggle

/**
 * The hidden surface, and the two hooks that put it in the app.
 *
 * `app/src/main` names these three and nothing else, so the F-Droid build answers with stubs in
 * `app/src/fdroid` and carries none of this code: no journal, no switch, no gesture, no report.
 *
 * The surface is discoverable only by deliberate action — five taps on the version row inside three
 * seconds — and never a trap: once revealed it stays in Settings, at the bottom, until the person
 * hides it again. The reveal lives in this app's private storage, so an uninstall takes it with
 * everything else.
 */
object DiagnosticsSurface {

    /** Adds the reveal gesture to the version row. */
    @Composable
    fun VersionTap(base: Modifier): Modifier {
        val context = LocalContext.current
        val diagnostics = remember(context) { DiagnosticsSupport.of(context) }
        val gate = remember(diagnostics) { TapGate() }
        return base.pointerInput(gate) {
            detectTapGestures {
                if (gate.tap(SystemClock.elapsedRealtime())) diagnostics.reveal()
            }
        }
    }

    /** The revealed section. Draws nothing before the gesture, which is the whole point of it. */
    @Composable
    fun Section() {
        val context = LocalContext.current
        val diagnostics = remember(context) { DiagnosticsSupport.of(context) }
        val revealed by diagnostics.revealed.collectAsStateWithLifecycle()
        if (!revealed) return
        val collecting by diagnostics.collecting.collectAsStateWithLifecycle()
        val limits = diagnostics.limits
        var report by remember { mutableStateOf<String?>(null) }
        var erased by remember { mutableStateOf(false) }
        var shareFailed by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.settings_section_diagnostics))
            SettingToggle(
                stringResource(R.string.settings_diagnostics_switch),
                stringResource(R.string.settings_diagnostics_switch_note),
                collecting,
            ) { on ->
                diagnostics.setCollecting(on)
                erased = false
            }
            Spacer(Modifier.height(10.dp))
            // What is recorded is stated whether or not the switch is on: somebody deciding has to be
            // able to read it without turning collection on first.
            Note(stringResource(R.string.settings_diagnostics_collects))
            if (!collecting) {
                Spacer(Modifier.height(8.dp))
                Note(stringResource(R.string.settings_diagnostics_off))
            }
            Spacer(Modifier.height(8.dp))
            Note(stringResource(R.string.settings_diagnostics_excludes))
            Spacer(Modifier.height(8.dp))
            Note(stringResource(R.string.settings_diagnostics_transport))
            Spacer(Modifier.height(8.dp))
            Note(
                stringResource(
                    R.string.settings_diagnostics_limits,
                    limits.entries,
                    (limits.bytes / 1024).toInt(),
                    limits.days,
                )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { report = diagnostics.report() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_diagnostics_view))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    diagnostics.erase()
                    erased = true
                    report = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_diagnostics_erase))
            }
            if (erased) {
                Spacer(Modifier.height(8.dp))
                Note(stringResource(R.string.settings_diagnostics_erased))
            }
            TextButton(
                onClick = { diagnostics.hide() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_diagnostics_hide))
            }
        }

        val shown = report
        if (shown != null)
            AlertDialog(
                onDismissRequest = { report = null },
                title = { Text(stringResource(R.string.settings_diagnostics_report_title)) },
                text = {
                    Column(
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    ) {
                        Note(stringResource(R.string.settings_diagnostics_report_intro))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            shown,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (shareFailed) {
                            Spacer(Modifier.height(10.dp))
                            Note(stringResource(R.string.settings_diagnostics_share_failed))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            shareFailed = !share(context, shown)
                        }
                    ) {
                        Text(stringResource(R.string.settings_diagnostics_share))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { report = null }) {
                        Text(stringResource(R.string.common_done))
                    }
                },
            )
    }

    /** Hands the journal to whichever app the person picks, as text. There is no other exit. */
    private fun share(context: android.content.Context, json: String): Boolean =
        try {
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        context.getString(R.string.settings_diagnostics_share_subject),
                    )
                }
            context.startActivity(
                Intent.createChooser(
                    send,
                    context.getString(R.string.settings_diagnostics_share_chooser),
                )
            )
            true
        } catch (e: Exception) {
            false
        }

    @Composable
    private fun Note(text: String) {
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    /**
     * Records how long this process took to build its first screen, and hands the observers the app's
     * status flow. Called once from the activity, outside any screen, so the number covers the whole
     * start and not only the moment Settings was opened.
     */
    @Composable
    fun Host(runtime: NightfallRuntime) {
        val context = LocalContext.current
        val diagnostics = remember(context) { DiagnosticsSupport.of(context) }
        LaunchedEffect(diagnostics) {
            diagnostics.attach(runtime)
            diagnostics.recordStartup()
        }
    }
}
