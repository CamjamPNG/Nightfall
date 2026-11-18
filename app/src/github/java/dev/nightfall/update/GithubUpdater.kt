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
 * With [automatic] on, which is this channel's default, the download and the install need no tap.
 * The install itself waits for one window: playback stopped, or the next launch. An install kills
 * the process, so an unattended one during playback would silence the app in the middle of the
 * night; [UpdateAutomation.mayInstallNow] is the rule and the tests pin it. A tap on Update is the
 * user asking and is not held back.
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
    private val _automatic = MutableStateFlow(UpdatePrefs.automatic(app))
    private val _log = MutableStateFlow(UpdateLogView())

    /** The last playback fact the app reported, and whether it has reported one at all. */
    @Volatile private var playback = Playback.UNKNOWN

    private var pending: AvailableUpdate? = null

    /** The verified download this channel is holding, on disk under the cache directory. */
    private var verified: PendingInstall? = null

    /**
     * The version this channel last handed to Android, which is what the "What's new" owed after an
     * update is owed for. A fresh install from GitHub never sets it: those notes were never news.
     */
    private var arrived = UpdatePrefs.takeArrival(app)

    /** When the current APK was handed to Android, so a row that never got a verdict can expire. */
    @Volatile private var handedOverAt = 0L

    override val supported = true
    override val installedVersion: String = "${installed.versionName} (${installed.versionCode})"
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    override val hiddenBannerVersion: StateFlow<String?> = _hiddenBanner.asStateFlow()
    override val automatic: StateFlow<Boolean> = _automatic.asStateFlow()
    override val log: StateFlow<UpdateLogView> = _log.asStateFlow()

    init {
        _log.value = logView(UpdatePrefs.log(app))
        UpdatePrefs.takeFailure(app)?.let { failure -> _state.value = UpdateState.Failed(failure) }
        // A verdict that arrives while this process is alive updates the row immediately; the copy
        // in preferences is what covers a process Android killed mid-install.
        scope.launch {
            InstallResults.results.collect { result ->
                if (result.awaitingConfirmation) return@collect
                // The verdict this process just applied must not be replayed as a stale failure
                // on the next foreground; the preferences copy exists for a killed process only.
                UpdatePrefs.takeFailure(app)
                if (result.succeeded) {
                    // The record in preferences stays: the launch that reads it is the one that
                    // owes the owner these notes, and that launch is a new process.
                    arrived = result.versionName
                    forgetPending()
                    _state.value = UpdateState.Idle
                } else {
                    // The verified download stays, so a retry does not fetch ninety megabytes again.
                    _state.value = UpdateState.Failed(result.reason ?: "The update did not finish installing")
                }
            }
        }
    }

    override fun onForeground() {
        var current = _state.value
        if (current.isBusy()) {
            if (current !is UpdateState.Installing) return
            // A replacement kills this process, so an install that never got a verdict and has had
            // time to arrive did not happen: dropping the row is what keeps it from claiming an
            // install forever. The grace period leaves Android's own confirmation dialog alone.
            if (System.currentTimeMillis() - handedOverAt < INSTALL_VERDICT_GRACE_MS) return
            _state.value = UpdateState.Idle
            current = _state.value
        }
        val failure = UpdatePrefs.takeFailure(app)
        if (failure != null) {
            if (current !is UpdateState.Failed && current !is UpdateState.ReadyToInstall)
                _state.value = UpdateState.Failed(failure)
            return
        }
        _log.value = logView(UpdatePrefs.log(app))
        // A failure the owner has seen is theirs to retry: keeping the download is what makes that
        // cheap, not a licence to start another unattended attempt. Otherwise cancelling Android's
        // confirmation would only postpone the next prompt to the next foreground.
        if (current is UpdateState.Failed) return
        val resumed = resumeVerified()
        if (resumed != null) {
            // Downloaded in an earlier session and still newer than what is running: the window the
            // install waits for may already be open.
            _state.value = UpdateState.ReadyToInstall(resumed.versionName, resumed.versionCode)
            installIfWindowOpen()
            return
        }
        // Only a state without an answer of its own may be replaced by a check nobody asked for.
        // A recent-check notice holds no answer, so it passes; the intervals still decide.
        if (current !is UpdateState.Idle && current !is UpdateState.UpToDate && current !is UpdateState.RecentCheck)
            return
        check(manual = false)
    }

    override fun onPlayback(playing: Boolean) {
        playback = if (playing) Playback.PLAYING else Playback.STOPPED
        // The one transition that opens the install window.
        if (!playing) installIfWindowOpen()
    }

    override fun check(manual: Boolean) {
        val current = _state.value
        if (current.isBusy()) return
        val now = System.currentTimeMillis()
        val lastAttempt = UpdatePrefs.lastAttempt(app)
        if (!manual &&
            !UpdateAutomation.wantsAutomaticCheck(
                automatic = _automatic.value,
                now = now,
                lastAttempt = lastAttempt,
                lastSuccess = UpdatePrefs.lastSuccess(app),
                online = online(),
            )
        )
            return
        if (!CheckThrottle.allowsRequest(now, lastAttempt)) {
            if (manual) {
                val ready = verified ?: UpdatePrefs.pendingInstall(app)
                val known = pending
                _state.value =
                    when {
                        // A press inside the floor still has the download from last time.
                        ready != null && ready.versionCode > installed.versionCode ->
                            UpdateState.ReadyToInstall(ready.versionName, ready.versionCode)
                        known != null -> UpdateState.Available(known.versionName, known.versionCode, known.apk.bytes)
                        else ->
                            UpdateState.RecentCheck(
                                CheckThrottle.minutesSince(now, lastAttempt),
                                CheckThrottle.minutesUntilRequest(now, lastAttempt),
                            )
                    }
            }
            return
        }
        UpdatePrefs.recordAttempt(app, now)
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val manifest = ReleaseManifestParser.parse(ReleaseFetch.manifestText())
                UpdatePrefs.recordSuccess(app)
                // Every successful answer carries the log, and the log is what the app shows on a day
                // when nothing needs installing: it is kept whatever the version decision is.
                saveLog(manifest.log)
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
                if (_automatic.value) download(update)
                else _state.value = UpdateState.Available(update.versionName, update.versionCode, update.apk.bytes)
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
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return
        if (!Apks.canRequestPackageInstalls(app)) {
            failMissingPermission()
            return
        }
        val resumed = resumeVerified()
        if (resumed != null) {
            // Already downloaded and verified: a retry costs nothing but the install, and this is
            // the tap, so the install does not wait for playback.
            handToAndroid(resumed, guarded = false)
            return
        }
        val update = pending ?: return
        download(update)
    }

    override fun setAutomatic(enabled: Boolean) {
        if (_automatic.value == enabled) return
        UpdatePrefs.setAutomatic(app, enabled)
        _automatic.value = enabled
        if (!enabled) return
        val resumed = resumeVerified()
        if (resumed != null) {
            _state.value = UpdateState.ReadyToInstall(resumed.versionName, resumed.versionCode)
            installIfWindowOpen()
            return
        }
        // Turning the setting on applies to the release this session already found. Asking GitHub
        // again would spend both pacing windows to learn what is already on screen.
        val known = pending
        if (known != null && !_state.value.isBusy()) {
            download(known)
            return
        }
        check(manual = false)
    }

    override fun dismiss() {
        when (val current = _state.value) {
            is UpdateState.Available -> hide(current.versionName)
            is UpdateState.ReadyToInstall -> hide(current.versionName)
            is UpdateState.Failed -> _state.value = UpdateState.Idle
            is UpdateState.RecentCheck -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    override fun dismissLog() {
        _log.value.celebrate ?: return
        UpdatePrefs.recordLogSeen(app, installed.versionName)
        _log.value = _log.value.copy(celebrate = null)
    }

    override fun openInstallPermission() = PackageInstalls.openUnknownSourcesSettings(app)

    private fun hide(versionName: String) {
        UpdatePrefs.hideBanner(app, versionName)
        _hiddenBanner.value = versionName
    }

    private fun download(update: AvailableUpdate) {
        _state.value = UpdateState.Downloading(update.versionName, 0f)
        scope.launch {
            val target = File(app.cacheDir, "updates/${update.apk.file}")
            try {
                target.parentFile?.let { folder ->
                    folder.listFiles()?.forEach { stale -> if (stale != target) stale.delete() }
                }
                ReleaseFetch.download(update.apk.url, target, update.apk.bytes) { fraction ->
                    _state.value = UpdateState.Downloading(update.versionName, fraction)
                }
                UpdatePolicy.verifyDownloaded(update, Apks.inspect(app, target), installed)
                val ready = PendingInstall(update.versionName, update.versionCode, update.apk.file)
                verified = ready
                UpdatePrefs.recordPendingInstall(app, ready)
                _state.value = UpdateState.ReadyToInstall(ready.versionName, ready.versionCode)
                installIfWindowOpen()
            } catch (e: UpdateProblem) {
                target.delete()
                forgetPending()
                _state.value = UpdateState.Failed(e.message ?: "This update was refused")
            } catch (e: IOException) {
                target.delete()
                forgetPending()
                _state.value = UpdateState.Failed("The download failed: ${e.message ?: "connection lost"}")
            } catch (e: Exception) {
                target.delete()
                forgetPending()
                _state.value =
                    UpdateState.Failed("The update could not be installed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * The only place an install starts without being asked. A refusal here is not a failure: the
     * update keeps waiting and the row says which window it is waiting for.
     */
    private fun installIfWindowOpen() {
        if (_state.value !is UpdateState.ReadyToInstall) return
        if (!UpdateAutomation.mayInstallNow(_automatic.value, playback)) return
        if (!Apks.canRequestPackageInstalls(app)) {
            failMissingPermission()
            return
        }
        val ready = verified ?: resumeVerified()
        if (ready == null) {
            _state.value = UpdateState.Idle
            return
        }
        handToAndroid(ready, guarded = true)
    }

    private fun handToAndroid(ready: PendingInstall, guarded: Boolean) {
        val file = File(app.cacheDir, "updates/${ready.file}")
        if (!file.isFile) {
            forgetPending()
            _state.value =
                UpdateState.Failed(
                    "The downloaded update is no longer on this device; the next check downloads it again"
                )
            return
        }
        handedOverAt = System.currentTimeMillis()
        UpdatePrefs.recordArrival(app, ready.versionName)
        _state.value = UpdateState.Installing(ready.versionName)
        scope.launch {
            try {
                val outcome =
                    PackageInstalls.install(app, file, ready.versionName) {
                        UpdateAutomation.mayCommitNow(guarded, _automatic.value, playback)
                    }
                if (outcome == InstallWindow.Outcome.DEFERRED) {
                    // Playback resumed while the APK was being copied. The session is abandoned, so
                    // the update goes back to waiting instead of killing the audio it waited for.
                    handedOverAt = 0L
                    _state.value = UpdateState.ReadyToInstall(ready.versionName, ready.versionCode)
                    // Playback may have stopped again while the session was being abandoned, and
                    // that transition would have found the row still installing. Re-ask rather than
                    // wait for an event that may not come: this refuses unless the setting is on and
                    // playback is stopped, and only a guarded install can reach here.
                    installIfWindowOpen()
                }
            } catch (e: Exception) {
                _state.value =
                    UpdateState.Failed("The update could not be installed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun failMissingPermission() {
        _state.value =
            UpdateState.Failed(
                "Allow Nightfall to install unknown apps, then tap Update again",
                FailureAction.ALLOW_INSTALLS,
            )
    }

    /** The verified download from this or an earlier session, if it is still on disk and still newer. */
    private fun resumeVerified(): PendingInstall? {
        val saved = verified ?: UpdatePrefs.pendingInstall(app) ?: return null
        val present = File(app.cacheDir, "updates/${saved.file}").isFile
        if (!UpdateAutomation.resumesAfterRestart(saved.versionCode, installed.versionCode, present)) {
            // Applied already, or deleted by Android: either way the record is spent.
            if (saved.versionCode <= installed.versionCode || !present) forgetPending()
            return null
        }
        verified = saved
        return saved
    }

    private fun forgetPending() {
        verified = null
        UpdatePrefs.clearPendingInstall(app)
    }

    private fun saveLog(entries: List<UpdateLogEntry>) {
        if (entries.isEmpty()) return
        UpdatePrefs.recordLog(app, entries)
        _log.value = logView(entries)
    }

    /**
     * The log the app holds, plus whether this build is one the log describes and the owner has not
     * read it yet — the case a "What's new" view is owed once, after the app updated itself.
     */
    private fun logView(entries: List<UpdateLogEntry>): UpdateLogView {
        val newest = entries.firstOrNull()
        val owed =
            newest != null &&
                newest.notes.isNotEmpty() &&
                newest.version == installed.versionName &&
                arrived == installed.versionName &&
                UpdatePrefs.logSeen(app) != installed.versionName
        return UpdateLogView(entries, if (owed) newest?.version else null)
    }

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

/** How long an install may sit without a verdict before a foreground entry treats it as lost. */
private const val INSTALL_VERDICT_GRACE_MS = 2 * 60 * 1000L
