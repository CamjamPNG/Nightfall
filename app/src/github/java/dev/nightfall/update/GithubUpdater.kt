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
 * The GitHub channel's updater. It reads the release manifest at most once every six hours — on a
 * foreground hint or a button press, both through [check] — downloads the single APK the manifest
 * names for this device, checks it against the manifest and against the installed app's signer, and
 * hands it to Android.
 *
 * Every byte of I/O happens on [Dispatchers.IO]; nothing here touches the audio thread, and no
 * alarm, worker or background service is created, so an idle app never wakes the radio.
 */
class GithubUpdater(context: Context) : UpdateApi {
    private val app = context.applicationContext
    private val installed = Apks.installed(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val _hiddenBanner = MutableStateFlow(UpdatePrefs.hiddenBanner(app))
    private var pending: AvailableUpdate? = null

    override val supported = true
    override val installedVersion: String = "${installed.versionName} (${installed.versionCode})"
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    override val hiddenBannerVersion: StateFlow<String?> = _hiddenBanner.asStateFlow()

    init {
        UpdatePrefs.takeFailure(app)?.let { _state.value = UpdateState.Failed(it) }
        // A verdict that arrives while this process is alive updates the row immediately; the copy
        // in preferences is what covers a process Android killed mid-install.
        scope.launch {
            InstallResults.results.collect { result ->
                if (result.awaitingConfirmation) return@collect
                _state.value =
                    if (result.succeeded) UpdateState.Idle
                    else UpdateState.Failed(result.reason ?: "The update did not finish installing")
            }
        }
    }

    override fun onForeground() {
        val failure = UpdatePrefs.takeFailure(app)
        if (failure != null && _state.value !is UpdateState.Installing) {
            _state.value = UpdateState.Failed(failure)
            return
        }
        val current = _state.value
        if (current is UpdateState.Installing || current is UpdateState.Downloading || current is UpdateState.Checking)
            return
        check(manual = false)
    }

    override fun check(manual: Boolean) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading || current is UpdateState.Installing)
            return
        val now = System.currentTimeMillis()
        val lastAttempt = UpdatePrefs.lastAttempt(app)
        if (!CheckThrottle.allows(now, lastAttempt)) {
            if (manual) {
                val known = pending
                _state.value =
                    if (known != null)
                        UpdateState.Available(known.versionName, known.versionCode, known.apk.bytes)
                    else UpdateState.RecentCheck(CheckThrottle.minutesSince(now, lastAttempt))
            }
            return
        }
        if (!manual && !online()) return
        UpdatePrefs.recordAttempt(app, now)
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val manifest = ReleaseManifestParser.parse(ReleaseFetch.manifestText())
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
                _state.value = UpdateState.Available(update.versionName, update.versionCode, update.apk.bytes)
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
                target.parentFile?.let { folder -> folder.listFiles()?.forEach { stale -> stale.delete() } }
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
                UpdatePrefs.hideBanner(app, current.versionName)
                _hiddenBanner.value = current.versionName
            }
            is UpdateState.Failed -> _state.value = UpdateState.Idle
            is UpdateState.RecentCheck -> _state.value = UpdateState.Idle
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
}

/** One process-wide updater, so a check or download survives tab switches. */
object UpdateSupport {
    private var instance: UpdateApi? = null

    fun of(context: Context): UpdateApi =
        instance ?: synchronized(this) {
            instance ?: GithubUpdater(context.applicationContext).also { instance = it }
        }
}
