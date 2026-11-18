// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.content.Context
import android.content.pm.PackageInstaller
import kotlinx.serialization.json.Json

/**
 * The only state the update channel keeps on disk: whether it updates on its own, when it last
 * asked GitHub, what the installer said after the app was replaced, which release banner the user
 * hid, the changelog the last manifest read carried, and the verified download still waiting for a
 * window to install. All of it is private preferences, never leaves the device, and the installer
 * outcome is cleared as it is read.
 */
object UpdatePrefs {
    private const val FILE = "nightfall_update"
    private const val KEY_STATUS = "install_status"
    private const val KEY_MESSAGE = "install_message"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_HIDDEN_BANNER = "hidden_banner_version"
    private const val KEY_AUTOMATIC = "automatic"
    private const val KEY_LOG = "release_log"
    private const val KEY_LOG_SEEN = "log_seen_version"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_CODE = "pending_code"
    private const val KEY_PENDING_FILE = "pending_file"
    private const val KEY_ARRIVED = "arrived_version"

    private val json = Json { ignoreUnknownKeys = true }

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

    /** Recorded before the request, so a fetch or parse that fails also spends the rate floor. */
    fun recordAttempt(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, now).apply()
    }

    /** The last answer that actually arrived: only this starts the six-hour interval. */
    fun lastSuccess(context: Context): Long = prefs(context).getLong(KEY_LAST_SUCCESS, 0L)

    fun recordSuccess(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_SUCCESS, now).apply()
    }

    fun hideBanner(context: Context, versionName: String) {
        prefs(context).edit().putString(KEY_HIDDEN_BANNER, versionName).apply()
    }

    fun hiddenBanner(context: Context): String? = prefs(context).getString(KEY_HIDDEN_BANNER, null)

    fun automatic(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOMATIC, UpdateAutomation.AUTOMATIC_BY_DEFAULT)

    fun setAutomatic(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOMATIC, enabled).apply()
    }

    /** The changelog the app shows. A copy that cannot be read is an empty log, never a crash. */
    fun log(context: Context): List<UpdateLogEntry> {
        val stored = prefs(context).getString(KEY_LOG, null) ?: return emptyList()
        return try {
            val parsed = json.decodeFromString<List<UpdateLogEntry>>(stored)
            UpdateLogParser.sanitize(parsed)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun recordLog(context: Context, entries: List<UpdateLogEntry>) {
        if (entries.isEmpty()) return
        prefs(context).edit().putString(KEY_LOG, json.encodeToString(entries)).apply()
    }

    fun logSeen(context: Context): String? = prefs(context).getString(KEY_LOG_SEEN, null)

    fun recordLogSeen(context: Context, versionName: String) {
        prefs(context).edit().putString(KEY_LOG_SEEN, versionName).apply()
    }

    /** The version this channel handed to Android, so the next launch can tell an update from a fresh install. */
    fun recordArrival(context: Context, versionName: String) {
        prefs(context).edit().putString(KEY_ARRIVED, versionName).apply()
    }

    fun takeArrival(context: Context): String? {
        val stored = prefs(context)
        val version = stored.getString(KEY_ARRIVED, null) ?: return null
        stored.edit().remove(KEY_ARRIVED).apply()
        return version.ifBlank { null }
    }

    fun pendingInstall(context: Context): PendingInstall? {
        val stored = prefs(context)
        val version = stored.getString(KEY_PENDING_VERSION, null) ?: return null
        val code = stored.getInt(KEY_PENDING_CODE, 0)
        val file = stored.getString(KEY_PENDING_FILE, null) ?: return null
        if (version.isBlank() || code <= 0 || file.isBlank()) return null
        return PendingInstall(version, code, file)
    }

    fun recordPendingInstall(context: Context, install: PendingInstall) {
        prefs(context)
            .edit()
            .putString(KEY_PENDING_VERSION, install.versionName)
            .putInt(KEY_PENDING_CODE, install.versionCode)
            .putString(KEY_PENDING_FILE, install.file)
            .apply()
    }

    fun clearPendingInstall(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_CODE)
            .remove(KEY_PENDING_FILE)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
