// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.content.Context
import android.content.pm.PackageInstaller

/**
 * The only state the update channel keeps on disk: when it last asked GitHub, and what the
 * installer said after the app was replaced. Both live in private preferences, never leave the
 * device, and are cleared as they are read.
 */
object UpdatePrefs {
    private const val FILE = "nightfall_update"
    private const val KEY_STATUS = "install_status"
    private const val KEY_MESSAGE = "install_message"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_DISMISSED = "dismissed_version"

    fun record(context: Context, status: Int, message: String) {
        prefs(context).edit().putInt(KEY_STATUS, status).putString(KEY_MESSAGE, message).apply()
    }

    /** Returns the installer's failure, if the last install attempt did not succeed, and forgets it. */
    fun takeFailure(context: Context): String? {
        val stored = prefs(context)
        if (!stored.contains(KEY_STATUS)) return null
        val status = stored.getInt(KEY_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = stored.getString(KEY_MESSAGE, null)
        stored.edit().remove(KEY_STATUS).remove(KEY_MESSAGE).apply()
        if (status == PackageInstaller.STATUS_SUCCESS || status == PackageInstaller.STATUS_PENDING_USER_ACTION) return null
        return message?.takeIf { it.isNotBlank() } ?: "The last update did not finish installing"
    }

    fun lastCheck(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun recordCheck(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** A version the user asked not to be told about again until the app restarts. */
    fun dismiss(context: Context, versionName: String) {
        prefs(context).edit().putString(KEY_DISMISSED, versionName).apply()
    }

    fun dismissed(context: Context): String? = prefs(context).getString(KEY_DISMISSED, null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
