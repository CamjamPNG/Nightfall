// SPDX-License-Identifier: MIT
package dev.nightfall.update

import kotlinx.coroutines.flow.StateFlow

/**
 * The update row's explicit state machine. Nothing in the audio path observes these; every
 * transition happens on a background dispatcher outside the render thread.
 */
sealed interface UpdateState {
    /** Nothing known yet, or the user dismissed the last result. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val versionName: String) : UpdateState

    /** A newer release exists and the app is not installing it on its own, so it waits for a tap. */
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val bytes: Long,
    ) : UpdateState

    data class Downloading(val versionName: String, val fraction: Float) : UpdateState

    /**
     * Downloaded and verified, waiting for the one window an unattended install may use: playback
     * stopped, or the next launch. The row says so rather than looking stuck.
     */
    data class ReadyToInstall(val versionName: String, val versionCode: Int) : UpdateState

    data class Installing(val versionName: String) : UpdateState

    /** A check was asked for inside the channel's rate floor, so nothing was requested. */
    data class RecentCheck(val minutesAgo: Int, val retryInMinutes: Int) : UpdateState

    data class Failed(val reason: String, val action: FailureAction = FailureAction.NONE) :
        UpdateState
}

/** True while a request or an install is in flight, so nothing else may start one. */
fun UpdateState.isBusy(): Boolean =
    this is UpdateState.Checking || this is UpdateState.Downloading || this is UpdateState.Installing

/** A downloaded, verified APK that has not been handed to Android yet. */
data class PendingInstall(val versionName: String, val versionCode: Int, val file: String)

/** The releases the last manifest read described, and the one the app owes the owner a word about. */
data class UpdateLogView(
    val entries: List<UpdateLogEntry> = emptyList(),
    /**
     * The release whose notes must be shown once because this build is the one it installed, or
     * null when there is nothing to celebrate: no notes, or they were already seen.
     */
    val celebrate: String? = null,
)

/** The one thing the user can do about a failure besides retrying. */
enum class FailureAction {
    NONE,

    /** Allow unknown apps for Nightfall, then try again. */
    ALLOW_INSTALLS,
}

/**
 * Implemented by each channel. The `github` build talks to the release manifest; the `fdroid`
 * build reports [supported] as false and does nothing, so no network code is compiled into it.
 */
interface UpdateApi {
    val supported: Boolean

    /** Human-readable installed version, such as `1.0.1.2 (4)`. */
    val installedVersion: String

    val state: StateFlow<UpdateState>

    /**
     * The release whose banner the user hid. Dismissing hides the notice, never the update: the
     * Settings row keeps offering it, and a newer release brings the banner back.
     */
    val hiddenBannerVersion: StateFlow<String?>

    /** Whether Nightfall may install new releases on its own. On by default; an owner can stop it. */
    val automatic: StateFlow<Boolean>

    /** The changelog the app already holds. Rendering it asks nobody anything. */
    val log: StateFlow<UpdateLogView>

    /** Checks for a newer release. [manual] is a button press rather than the app's own hint. */
    fun check(manual: Boolean)

    /** Downloads, verifies and installs the update found by the last [check], because a tap asked. */
    fun install()

    /** Called when the app comes to the foreground; implementations throttle this. */
    fun onForeground()

    /** A playback fact from the app. The install window is derived from it. */
    fun onPlayback(playing: Boolean)

    /** Turns the automatic check and install on or off. Turning it on applies to what is already known. */
    fun setAutomatic(enabled: Boolean)

    /** Hides the banner without discarding the result shown in Settings. */
    fun dismiss()

    /** The owner has read the notes the app owed them; they are not shown again for that release. */
    fun dismissLog()

    /** Opens the system screen that allows Nightfall to install packages. */
    fun openInstallPermission()
}

/** Whether the floating row should be on screen for [state], given the release the user hid. */
fun shouldShowBanner(state: UpdateState, hiddenVersion: String?): Boolean =
    when (state) {
        is UpdateState.Available -> state.versionName != hiddenVersion
        is UpdateState.ReadyToInstall -> state.versionName != hiddenVersion
        is UpdateState.Checking,
        is UpdateState.Downloading,
        is UpdateState.Installing -> true
        is UpdateState.Failed -> true
        is UpdateState.UpToDate,
        is UpdateState.RecentCheck,
        UpdateState.Idle -> false
    }
