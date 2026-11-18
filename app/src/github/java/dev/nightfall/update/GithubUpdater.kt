// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The GitHub channel's updater. It reads the release manifest once per app foreground after six
 * hours, or when the user asks, downloads the single APK the manifest names for this device, checks
 * it against the manifest and against the installed app's signer, and hands it to Android.
 *
 * Every byte of I/O happens on [Dispatchers.IO]; nothing here touches the audio thread, and no
 * alarm, worker or background service is created, so an idle app never wakes the radio.
 */
class GithubUpdater(context: Context) : UpdateApi {
    private val app = context.applicationContext
    private val installed = Apks.installed(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private var pending: AvailableUpdate? = null
    private var dismissed: String? = UpdatePrefs.dismissed(app)

    override val supported = true
    override val installedVersion: String = "${installed.versionName} (${installed.versionCode})"
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        UpdatePrefs.takeFailure(app)?.let { _state.value = UpdateState.Failed(it) }
    }

    override fun onForeground() {
        val current = _state.value
        if (current !is UpdateState.Idle && current !is UpdateState.UpToDate) return
        if (!online()) return
        if (System.currentTimeMillis() - UpdatePrefs.lastCheck(app) < CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    override fun check(manual: Boolean) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading || current is UpdateState.Installing)
            return
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val manifest = ReleaseManifestParser.parse(ReleaseFetch.manifestText())
                UpdatePrefs.recordCheck(app)
                if (manifest.versionCode <= installed.versionCode) {
                    pending = null
                    _state.value = UpdateState.UpToDate(installed.versionName)
                    return@launch
                }
                val update =
                    UpdatePolicy.nextUpdate(
                        manifest,
                        installed,
                        DeviceProfile(Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT),
                    )
                pending = update
                _state.value =
                    if (update.versionName == dismissed) UpdateState.Idle
                    else UpdateState.Available(update.versionName, update.versionCode, update.apk.bytes)
            } catch (e: UpdateProblem) {
                _state.value = UpdateState.Failed(e.message ?: "This update was refused")
            } catch (e: IOException) {
                _state.value =
                    UpdateState.Failed(
                        "Could not reach the release manifest: ${e.message ?: "no answer from GitHub"}"
                    )
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("The update check failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun install() {
        val update = pending ?: return
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return
        if (!Apks.canRequestPackageInstalls(app)) {
            _state.value =
                UpdateState.Failed(
                    "Allow Nightfall to install unknown apps, then tap Update again",
                    FailureAction.ALLOW_INSTALLS,
                )
            return
        }
        _state.value = UpdateState.Downloading(update.versionName, 0f)
        scope.launch {
            val target = File(app.cacheDir, "updates/${update.apk.file}")
            try {
                target.parentFile?.let { folder ->
                    folder.listFiles()?.forEach { stale -> stale.delete() }
                }
                ReleaseFetch.download(update.apk.url, target, update.apk.bytes) { fraction ->
                    _state.value = UpdateState.Downloading(update.versionName, fraction)
                }
                UpdatePolicy.verifyDownloaded(update, Apks.inspect(app, target), installed)
                _state.value = UpdateState.Installing(update.versionName)
                PackageInstalls.install(app, target, update.versionName)
            } catch (e: UpdateProblem) {
                target.delete()
                _state.value = UpdateState.Failed(e.message ?: "This update was refused")
            } catch (e: IOException) {
                target.delete()
                _state.value = UpdateState.Failed("The download failed: ${e.message ?: "connection lost"}")
            } catch (e: Exception) {
                target.delete()
                _state.value =
                    UpdateState.Failed("The update could not be installed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun dismiss() {
        when (val current = _state.value) {
            is UpdateState.Available -> {
                UpdatePrefs.dismiss(app, current.versionName)
                dismissed = current.versionName
                _state.value = UpdateState.Idle
            }
            is UpdateState.Failed -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    override fun openInstallPermission() = PackageInstalls.openUnknownSourcesSettings(app)

    private fun online(): Boolean {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}

/** One process-wide updater, so a check or download survives tab switches. */
object UpdateSupport {
    private var instance: UpdateApi? = null

    fun of(context: Context): UpdateApi =
        instance ?: synchronized(this) {
            instance ?: GithubUpdater(context.applicationContext).also { instance = it }
        }
}
