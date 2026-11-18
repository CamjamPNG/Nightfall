// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.content.Context
import android.content.pm.PackageInstaller

/**
 * The only state the update channel keeps on disk: when it last asked GitHub, what the installer
 * said after the app was replaced, and which release banner the user hid. All of it is private
 * preferences, never leaves the device, and the installer outcome is cleared as it is read.
 */
object UpdatePrefs {
    private const val FILE = "nightfall_update"
    private const val KEY_STATUS = "install_status"
    private const val KEY_MESSAGE = "install_message"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_HIDDEN_BANNER = "hidden_banner_version"

    fun record(context: Context, status: Int, message: String) {
        prefs(context).edit().putInt(KEY_STATUS, status).putString(KEY_MESSAGE, message).apply()
    }

    /** Returns the installer's failure, if the last attempt did not succeed, and forgets it. */
    fun takeFailure(context: Context): String? {
        val stored = prefs(context)
        if (!stored.contains(KEY_STATUS)) return null
        val status = stored.getInt(KEY_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = stored.getString(KEY_MESSAGE, null)
        stored.edit().remove(KEY_STATUS).remove(KEY_MESSAGE).apply()
        if (status == PackageInstaller.STATUS_SUCCESS || status == PackageInstaller.STATUS_PENDING_USER_ACTION)
            return null
        return message?.takeIf { it.isNotBlank() } ?: "The last update did not finish installing"
    }

    fun lastAttempt(context: Context): Long = prefs(context).getLong(KEY_LAST_ATTEMPT, 0L)

    /** Recorded before the request, so a fetch or parse that fails also spends the interval. */
    fun recordAttempt(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, now).apply()
    }

    fun hideBanner(context: Context, versionName: String) {
        prefs(context).edit().putString(KEY_HIDDEN_BANNER, versionName).apply()
    }

    fun hiddenBanner(context: Context): String? = prefs(context).getString(KEY_HIDDEN_BANNER, null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
