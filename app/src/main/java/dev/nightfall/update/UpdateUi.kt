// SPDX-License-Identifier: MIT
package dev.nightfall.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt

/** The one updater instance for this process, provided by whichever channel is built. */
@Composable
fun rememberUpdateApi(): UpdateApi {
    val context = LocalContext.current
    return remember(context) { UpdateSupport.of(context.applicationContext) }
}

/**
 * A non-modal row over the app while an update is available, busy or refused. It never opens a
 * dialog over playback and the user can dismiss it at any time.
 */
@Composable
fun UpdateBanner(api: UpdateApi, modifier: Modifier = Modifier) {
    if (!api.supported) return
    val state by api.state.collectAsStateWithLifecycle()
    val hidden by api.hiddenBannerVersion.collectAsStateWithLifecycle()
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
                Text(detail(current), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** The Settings section: installed version, one action, and what happened. */
@Composable
fun UpdateSettings(api: UpdateApi) {
    val state by api.state.collectAsStateWithLifecycle()
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
                "The only network access in Nightfall: it reads a release manifest from GitHub and, only " +
                    "when you tap Update, downloads one APK. Nothing about you is sent.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
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
                if (current is UpdateState.Failed && current.action == FailureAction.ALLOW_INSTALLS)
                    Button(onClick = { api.openInstallPermission() }) { Text("Open settings") }
            }
            Spacer(Modifier.height(8.dp))
            Text(statusLine(current), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (current is UpdateState.Downloading) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { current.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun title(state: UpdateState): String =
    when (state) {
        is UpdateState.Available -> "Nightfall ${state.versionName} is available"
        is UpdateState.Downloading -> "Downloading Nightfall ${state.versionName}"
        is UpdateState.Installing -> "Installing Nightfall ${state.versionName}"
        is UpdateState.Checking -> "Checking for updates"
        is UpdateState.Failed -> "Update not applied"
        is UpdateState.UpToDate -> "Nightfall is up to date"
        is UpdateState.RecentCheck -> "Checked recently"
        UpdateState.Idle -> ""
    }

private fun detail(state: UpdateState): String =
    when (state) {
        is UpdateState.Available -> "${megabytes(state.bytes)} MB · verified against the signed release"
        is UpdateState.Downloading -> "${(state.fraction * 100).roundToInt()}% · verified as it arrives"
        is UpdateState.Installing -> "Android is replacing the installed app; playback stops when it restarts"
        is UpdateState.Checking -> "Reading the latest release manifest"
        is UpdateState.Failed -> state.reason
        is UpdateState.UpToDate -> "Version ${state.versionName} is the newest release"
        is UpdateState.RecentCheck -> "Nothing was requested this time"
        UpdateState.Idle -> ""
    }

private fun statusLine(state: UpdateState): String =
    when (state) {
        is UpdateState.Idle -> "Nightfall asks GitHub at most once every six hours, and only while it is open."
        is UpdateState.Checking -> "Checking…"
        is UpdateState.UpToDate -> "Nightfall ${state.versionName} is the latest release."
        is UpdateState.Available -> "Nightfall ${state.versionName} is available (${megabytes(state.bytes)} MB)."
        is UpdateState.Downloading -> "Downloading… ${(state.fraction * 100).roundToInt()}%"
        is UpdateState.Installing -> "Installing. Nightfall restarts when Android finishes."
        is UpdateState.RecentCheck ->
            "Nightfall asked GitHub ${state.minutesAgo} minute(s) ago. It checks at most once " +
                "every six hours; the next request is allowed after that."
        is UpdateState.Failed -> state.reason
    }

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "0" else String.format(Locale.US, "%.0f", bytes / 1_000_000.0)
