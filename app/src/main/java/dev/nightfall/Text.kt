// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context

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

/**
 * A time of day as this device writes one: `18:30` where the phone is set to a 24-hour clock,
 * `6:30 PM` where it is not. The minute of the day is 0–1439.
 *
 * This is the device's own formatter rather than a pattern of ours, so the setting a person already
 * made for every other clock on their phone is the one this app follows.
 */
internal fun clockLabel(context: Context, minuteOfDay: Int): String {
    val calendar =
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(java.util.Calendar.MINUTE, minuteOfDay % 60)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}

/** The installed version name, so a copy string cannot drift from the build the way `1.0` did. */@Suppress("DEPRECATION")
internal fun installedVersion(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

/** The route as Settings shows it, device name included. */
internal fun outputLabel(context: Context, route: OutputRoute): String =
    when (route.kind) {
        OutputRoute.Kind.BLUETOOTH ->
            if (route.deviceName.isEmpty()) context.getString(R.string.output_bluetooth)
            else context.getString(R.string.output_bluetooth_named, route.deviceName)
        OutputRoute.Kind.WIRED -> context.getString(R.string.output_wired)
        OutputRoute.Kind.SPEAKER -> context.getString(R.string.output_speaker)
        OutputRoute.Kind.DEVICE ->
            route.deviceName.ifEmpty { context.getString(R.string.output_system) }
        OutputRoute.Kind.SYSTEM -> context.getString(R.string.output_system)
    }

/** The route as the now-playing card has room for: the transport, not which earbuds. */
internal fun outputShortLabel(context: Context, route: OutputRoute): String =
    if (route.kind == OutputRoute.Kind.BLUETOOTH) context.getString(R.string.output_bluetooth)
    else outputLabel(context, route)
