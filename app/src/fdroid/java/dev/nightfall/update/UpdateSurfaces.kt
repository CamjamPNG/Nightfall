// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow

/**
 * The F-Droid channel's half of the update surface. There is no updater, no manifest URL, no
 * downloader and no network permission in this build, so this file offers the same three names the
 * GitHub channel does and shows nothing but the installed version and where updates come from.
 */

/** Nothing to show over the app in a build that cannot update itself. */
@Composable
fun UpdateOverlay(modifier: Modifier = Modifier) = Unit

/**
 * Nothing to tell the channel: it has no state and never touches the network. The playback flow is
 * accepted and ignored so the activity names one surface in both builds.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun UpdateForegroundEffect(playing: Flow<Boolean>) = Unit

@Composable
fun UpdateSettingsSection() {
    val context = LocalContext.current
    val installed =
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code =
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
                else @Suppress("DEPRECATION") info.versionCode
            "${info.versionName ?: "unknown"} ($code)"
        } catch (e: Exception) {
            "unknown"
        }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Installed version: $installed", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "This build has no update checker, no network permission and no network code. " +
                "Updates arrive through F-Droid or your app store.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
