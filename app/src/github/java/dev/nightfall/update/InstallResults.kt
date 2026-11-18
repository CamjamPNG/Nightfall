// SPDX-License-Identifier: MIT
package dev.nightfall.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What Android's installer said about the last attempt. */
data class InstallResult(
    /** The version that was being installed. */
    val versionName: String,
    val succeeded: Boolean,
    /** True while the system is waiting for the user to confirm the install. */
    val awaitingConfirmation: Boolean,
    val reason: String?,
)

/**
 * Carries installer verdicts back into the live updater state, so a rejection or a cancelled
 * confirmation is shown immediately instead of waiting for the next process start. The receiver
 * also writes the outcome to [UpdatePrefs], which is what covers a process that was killed.
 */
object InstallResults {
    private val flow = MutableSharedFlow<InstallResult>(extraBufferCapacity = 4)
    val results: SharedFlow<InstallResult> = flow.asSharedFlow()

    fun publish(result: InstallResult) {
        flow.tryEmit(result)
    }
}
