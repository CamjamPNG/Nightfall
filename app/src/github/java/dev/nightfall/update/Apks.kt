// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/**
 * Reads what Android says about the installed app and about a downloaded APK. The signing
 * certificate of the installed app is the anchor of the whole update channel: an update is only
 * installable if its signer is that same certificate.
 */
object Apks {
    @Suppress("DEPRECATION")
    private const val SIGNING_FLAGS =
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

    fun installed(context: Context): InstalledApp {
        val manager = context.packageManager
        val info = manager.getPackageInfo(context.packageName, SIGNING_FLAGS)
        return InstalledApp(
            packageName = context.packageName,
            versionName = info.versionName ?: "unknown",
            versionCode = info.versionCodeCompat(),
            certificateSha256 = digests(info).firstOrNull() ?: "",
        )
    }

    /** Reads package name, version and signer from the APK file itself, never from the manifest. */
    fun inspect(context: Context, file: File): DownloadedApk {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, SIGNING_FLAGS)
        val abis =
            try {
                ReleaseFetch.nativeAbis(file)
            } catch (e: Exception) {
                emptySet()
            }
        return DownloadedApk(
            sizeBytes = file.length(),
            sha256 = ReleaseFetch.sha256(file),
            packageName = info?.packageName,
            versionCode = info?.versionCodeCompat(),
            certificateSha256s = info?.let { digests(it) } ?: emptyList(),
            nativeAbis = abis,
        )
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        try {
            context.packageManager.canRequestPackageInstalls()
        } catch (e: Exception) {
            false
        }

    private fun digests(info: PackageInfo): List<String> {
        val signatures: Array<Signature> =
            if (Build.VERSION.SDK_INT >= 28) {
                val signing = info.signingInfo ?: return emptyList()
                signing.apkContentsSigners ?: return emptyList()
            } else {
                @Suppress("DEPRECATION") info.signatures ?: return emptyList()
            }
        return signatures.filterNotNull().map { ReleaseFetch.sha256(it.toByteArray()) }
    }
}

private fun PackageInfo.versionCodeCompat(): Int =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode.toInt() else @Suppress("DEPRECATION") versionCode
