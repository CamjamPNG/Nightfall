// SPDX-License-Identifier: MIT
package dev.nightfall.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nightfall.SettingToggle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/** One updater per process; the flaky part of remembering it belongs in one place. */
@Composable
private fun rememberUpdateApi(): UpdateApi {
    val context = LocalContext.current
    return remember(context) { UpdateSupport.of(context.applicationContext) }
}

/**
 * Hosts the update row over the app, and the one-time "What's new" after the app replaced itself.
 * Called from the activity, not from any screen.
 */
@Composable
fun UpdateOverlay(modifier: Modifier = Modifier) {
    val api = rememberUpdateApi()
    UpdateBanner(api, modifier)
    val log by api.log.collectAsStateWithLifecycle()
    if (log.celebrate != null) WhatsNewDialog(log, onDismiss = { api.dismissLog() }, celebrated = true)
}

/**
 * Tells the channel the app is in the foreground, which is when it may check on its own, and hands
 * it every playback transition: stopping playback is what opens the window an automatic install
 * waits for.
 */
@Composable
fun UpdateForegroundEffect(playing: Flow<Boolean>) {
    val api = rememberUpdateApi()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { api.onForeground() }
    LaunchedEffect(api, playing) { playing.collect { api.onPlayback(it) } }
}

/** The Settings section: installed version, the automatic setting, one action, and what happened. */
@Composable
fun UpdateSettingsSection() {
    UpdateSettings(rememberUpdateApi())
}

/**
 * A non-modal row over the app while an update is available, busy, waiting or refused. It never
 * opens a dialog over playback and the user can dismiss it at any time.
 */
@Composable
private fun UpdateBanner(api: UpdateApi, modifier: Modifier = Modifier) {
    if (!api.supported) return
    val state by api.state.collectAsStateWithLifecycle()
    val hidden by api.hiddenBannerVersion.collectAsStateWithLifecycle()
    val automatic by api.automatic.collectAsStateWithLifecycle()
    val current = state
    if (!shouldShowBanner(current, hidden)) return
    Card(
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .semantics { contentDescription = "Software update" },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title(current), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(detail(current, automatic), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (current) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { current.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is UpdateState.Installing -> {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateState.Available -> {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { api.install() }) { Text("Update") }
                    }
                    is UpdateState.ReadyToInstall ->
                        if (!automatic) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { api.install() }) { Text("Install") }
                        }
                    is UpdateState.Failed ->
                        if (current.action == FailureAction.ALLOW_INSTALLS) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { api.openInstallPermission() }) { Text("Open settings") }
                                OutlinedButton(onClick = { api.install() }) { Text("Try again") }
                            }
                        }
                    else -> Unit
                }
            }
            IconButton(
                onClick = { api.dismiss() },
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Dismiss update notice" },
            ) {
                Icon(Icons.Outlined.Close, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun UpdateSettings(api: UpdateApi) {
    val state by api.state.collectAsStateWithLifecycle()
    val automatic by api.automatic.collectAsStateWithLifecycle()
    val log by api.log.collectAsStateWithLifecycle()
    var readLog by remember { mutableStateOf(false) }
    val current = state
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Installed version: ${api.installedVersion}", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        if (!api.supported) {
            Text(
                "This build has no update checker. Updates arrive through F-Droid or your app store.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "The only network access in Nightfall: it reads a release manifest from GitHub and downloads " +
                    "one APK. Nothing about you is sent.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SettingToggle(
                title = "Update automatically",
                description = "Install a new release on its own, never while sound is playing.",
                checked = automatic,
            ) { api.setAutomatic(it) }
            Spacer(Modifier.height(6.dp))
            val busy =
                current is UpdateState.Checking ||
                    current is UpdateState.Downloading ||
                    current is UpdateState.Installing
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { api.check(manual = true) }, enabled = !busy) {
                    Text("Check for updates")
                }
                if (current is UpdateState.Available)
                    Button(onClick = { api.install() }) { Text("Update to ${current.versionName}") }
                if (current is UpdateState.ReadyToInstall)
                    Button(onClick = { api.install() }) { Text("Install ${current.versionName} now") }
                if (current is UpdateState.Failed && current.action == FailureAction.ALLOW_INSTALLS)
                    Button(onClick = { api.openInstallPermission() }) { Text("Open settings") }
            }
            Spacer(Modifier.height(8.dp))
            Text(statusLine(current, automatic), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (current is UpdateState.Downloading) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { current.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { readLog = true }) { Text("What's new") }
        }
    }
    if (readLog) WhatsNewDialog(log, onDismiss = { readLog = false }, celebrated = false)
}

/**
 * The in-app changelog: the releases the last manifest read described, newest first. Every line
 * comes from what the app already stored, so opening it reaches no network. An empty list says so
 * plainly instead of showing an empty page.
 */
@Composable
private fun WhatsNewDialog(log: UpdateLogView, onDismiss: () -> Unit, celebrated: Boolean) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(
                Modifier.widthIn(min = 280.dp, max = 420.dp).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (celebrated) "Nightfall is updated" else "What's new",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (log.entries.isEmpty()) {
                    Text(
                        "No release notes are stored yet. Nightfall keeps the list from the next release " +
                            "manifest it reads.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(log.entries, key = { it.version }) { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    if (entry.date.isEmpty()) "Nightfall ${entry.version}"
                                    else "Nightfall ${entry.version} · ${entry.date}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (entry.notes.isEmpty())
                                    Text(
                                        "No notes were published for this release.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                else
                                    entry.notes.forEach { note ->
                                        Text(
                                            "•  $note",
                                            fontSize = 11.5.sp,
                                            lineHeight = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

private fun title(state: UpdateState): String =
    when (state) {
        is UpdateState.Available -> "Nightfall ${state.versionName} is available"
        is UpdateState.Downloading -> "Downloading Nightfall ${state.versionName}"
        is UpdateState.ReadyToInstall -> "Nightfall ${state.versionName} is ready"
        is UpdateState.Installing -> "Installing Nightfall ${state.versionName}"
        is UpdateState.Checking -> "Checking for updates"
        is UpdateState.Failed -> "Update not applied"
        is UpdateState.UpToDate -> "Nightfall is up to date"
        is UpdateState.RecentCheck -> "Checked recently"
        UpdateState.Idle -> ""
    }

private fun detail(state: UpdateState, automatic: Boolean): String =
    when (state) {
        is UpdateState.Available -> "${megabytes(state.bytes)} MB · verified against the signed release"
        is UpdateState.Downloading -> "${(state.fraction * 100).roundToInt()}% · verified as it arrives"
        is UpdateState.ReadyToInstall ->
            if (automatic) "Installs by itself when sound is stopped, or next time you open Nightfall"
            else "Installs when you tap Install"
        is UpdateState.Installing -> "Android is replacing the installed app; playback stops when it restarts"
        is UpdateState.Checking -> "Reading the latest release manifest"
        is UpdateState.Failed -> state.reason
        is UpdateState.UpToDate -> "Version ${state.versionName} is the newest release"
        is UpdateState.RecentCheck -> "Nothing was requested this time"
        UpdateState.Idle -> ""
    }

private fun statusLine(state: UpdateState, automatic: Boolean): String =
    when (state) {
        is UpdateState.Idle ->
            if (automatic)
                "Nightfall checks and updates on its own, at most once every six hours while it is open, and " +
                    "never while sound is playing. A check you start is limited to one every fifteen minutes."
            else
                "Nightfall only updates when you ask it to. A check you start is limited to one every " +
                    "fifteen minutes."
        is UpdateState.Checking -> "Checking…"
        is UpdateState.UpToDate ->
            "Nightfall ${state.versionName} is the latest release." + if (automatic) "" else " Automatic updates are off."
        is UpdateState.Available ->
            "Nightfall ${state.versionName} is available (${megabytes(state.bytes)} MB)."
        is UpdateState.Downloading -> "Downloading… ${(state.fraction * 100).roundToInt()}%"
        is UpdateState.ReadyToInstall ->
            if (automatic)
                "Nightfall ${state.versionName} is downloaded and verified. It installs as soon as sound " +
                    "is stopped, so an update never cuts a night's audio short."
            else
                "Nightfall ${state.versionName} is downloaded and verified and waits for you, because " +
                    "automatic updates are off."
        is UpdateState.Installing -> "Installing. Nightfall restarts when Android finishes."
        is UpdateState.RecentCheck ->
            "Nightfall asked GitHub ${state.minutesAgo} minute(s) ago. A check you start is limited " +
                "to one every fifteen minutes; you can try again in ${state.retryInMinutes} minute(s)."
        is UpdateState.Failed -> state.reason
    }

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "0" else String.format(Locale.US, "%.0f", bytes / 1_000_000.0)
