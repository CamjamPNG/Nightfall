// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context

/**
 * Count-aware wording: a count of one takes the singular. English only, and deliberately so — the
 * app has no translation, and adding one would have to go through docs/LOCALIZATION.md first.
 */
internal fun plural(count: Int, one: String, many: String): String = if (count == 1) one else many

/**
 * A duration as `m:ss`, or `h:mm:ss` once it reaches an hour. The sleep timer accepts up to 24
 * hours, so the hours field matters: a 90-minute timer is `1:30:00`, not `90:00`.
 */
internal fun elapsedLabel(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val rest = (seconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$rest" else "$minutes:$rest"
}

/** The installed version name, so a copy string cannot drift from the build the way `1.0` did. */
@Suppress("DEPRECATION")
internal fun installedVersion(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

/** The route as Settings shows it, device name included. */
internal fun outputLabel(route: OutputRoute): String =
    when (route.kind) {
        OutputRoute.Kind.BLUETOOTH ->
            if (route.deviceName.isEmpty()) "Bluetooth" else "Bluetooth · ${route.deviceName}"
        OutputRoute.Kind.WIRED -> "Wired headphones"
        OutputRoute.Kind.SPEAKER -> "Built-in speaker"
        OutputRoute.Kind.DEVICE -> route.deviceName.ifEmpty { SYSTEM_OUTPUT }
        OutputRoute.Kind.SYSTEM -> SYSTEM_OUTPUT
    }

/** The route as the now-playing card has room for: the transport, not which earbuds. */
internal fun outputShortLabel(route: OutputRoute): String =
    if (route.kind == OutputRoute.Kind.BLUETOOTH) "Bluetooth" else outputLabel(route)

private const val SYSTEM_OUTPUT = "System audio output"
