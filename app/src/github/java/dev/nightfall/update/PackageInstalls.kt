// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Hands a verified APK to Android's PackageInstaller.
 *
 * On Android 12 and newer, with targetSdk 35, the `UPDATE_PACKAGES_WITHOUT_USER_ACTION` permission
 * declared and `USER_ACTION_NOT_REQUIRED` requested, an app updating itself completes without a
 * prompt. Devices whose OEM image still requires one answer with
 * [PackageInstaller.STATUS_PENDING_USER_ACTION], which is forwarded to the user.
 */
object PackageInstalls {
    /**
     * Copies [apk] into a session and hands it to Android. [windowOpen] is consulted once more after
     * the copy, right before the commit, because the copy is slow enough for the owner to start
     * playback: a closed window abandons the session and reports [InstallWindow.Outcome.DEFERRED].
     */
    fun install(
        context: Context,
        apk: File,
        versionName: String,
        windowOpen: () -> Boolean,
    ): InstallWindow.Outcome {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
                if (Build.VERSION.SDK_INT >= 31)
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            return InstallWindow.copyThenCommit(
                copy = {
                    apk.inputStream().use { input ->
                        session.openWrite("nightfall-update.apk", 0, apk.length()).use { output ->
                            input.copyTo(output, 64 * 1024)
                            session.fsync(output)
                        }
                    }
                },
                windowOpen = windowOpen,
                commit = { session.commit(statusIntent(context, sessionId, versionName).intentSender) },
                abandon = { session.abandon() },
            )
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val reason = "This device has no screen for app install permission"
            UpdatePrefs.record(context, PackageInstaller.STATUS_FAILURE, reason)
            InstallResults.publish(InstallResult("", succeeded = false, awaitingConfirmation = false, reason = reason))
        }
    }

    private fun statusIntent(context: Context, sessionId: Int, versionName: String): PendingIntent {
        val intent =
            Intent(context, UpdateInstallReceiver::class.java)
                .setAction(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(UpdateInstallReceiver.EXTRA_VERSION, versionName)
        val flags =
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}

/** Receives the installer's verdict, including a request for the user to confirm. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                UpdatePrefs.record(context, PackageInstaller.STATUS_SUCCESS, version)
                InstallResults.publish(InstallResult(version, succeeded = true, awaitingConfirmation = false, reason = null))
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                UpdatePrefs.record(context, PackageInstaller.STATUS_PENDING_USER_ACTION, version)
                InstallResults.publish(
                    InstallResult(version, succeeded = false, awaitingConfirmation = true, reason = null)
                )
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                if (confirm != null)
                    try {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        val reason = "Android needs a confirmation that could not be shown"
                        UpdatePrefs.record(context, PackageInstaller.STATUS_FAILURE, reason)
                        InstallResults.publish(InstallResult(version, succeeded = false, awaitingConfirmation = false, reason = reason))
                    }
            }
            else -> {
                val reason = reason(intent)
                UpdatePrefs.record(context, PackageInstaller.STATUS_FAILURE, reason)
                InstallResults.publish(InstallResult(version, succeeded = false, awaitingConfirmation = false, reason = reason))
            }
        }
    }

    private fun reason(intent: Intent): String {
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        return when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install" + message.suffix()
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Android refused the install: the file does not match the installed Nightfall" + message.suffix()
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "Android refused the install: this device is incompatible" + message.suffix()
            PackageInstaller.STATUS_FAILURE_INVALID ->
                "Android refused the install: the file is not a valid Nightfall APK" + message.suffix()
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                "Android refused the install: not enough free storage" + message.suffix()
            else -> "Android refused the install" + message.suffix()
        }
    }

    private fun String?.suffix(): String = if (isNullOrBlank()) "" else " ($this)"

    companion object {
        const val ACTION_STATUS = "dev.nightfall.update.INSTALL_STATUS"
        const val EXTRA_VERSION = "dev.nightfall.update.VERSION"
    }
}
