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

    data class Available(
        val versionName: String,
        val versionCode: Int,
        val bytes: Long,
    ) : UpdateState

    data class Downloading(val versionName: String, val fraction: Float) : UpdateState

    data class Installing(val versionName: String) : UpdateState

    data class Failed(val reason: String, val action: FailureAction = FailureAction.NONE) :
        UpdateState
}

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

    /** Checks for a newer release. [manual] is a button press rather than a background hint. */
    fun check(manual: Boolean)

    /** Downloads, verifies and installs the update found by the last [check]. */
    fun install()

    /** Called when the app comes to the foreground; implementations throttle this. */
    fun onForeground()

    /** Hides the banner without forgetting the result shown in Settings. */
    fun dismiss()

    /** Opens the system screen that allows Nightfall to install packages. */
    fun openInstallPermission()
}
