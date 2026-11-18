// SPDX-License-Identifier: MIT
package dev.nightfall.update

/**
 * Copy, then read the window again, then commit.
 *
 * Writing a ninety-megabyte APK into a `PackageInstaller` session takes seconds, and Play and the
 * media-session controls stay live the whole time. So the window an unattended install was granted
 * before the copy is re-read here, immediately before Android replaces the app: playback that
 * resumed during the copy abandons the session and defers, instead of killing the audio it was
 * waiting for. The sequence is split out of the installer so the ordering itself is tested rather
 * than assumed.
 */
object InstallWindow {
    enum class Outcome {
        /** Android has the session; the replacement is under way. */
        COMMITTED,

        /** The window closed during the copy. The session is abandoned and nothing was installed. */
        DEFERRED,
    }

    fun copyThenCommit(
        copy: () -> Unit,
        windowOpen: () -> Boolean,
        commit: () -> Unit,
        abandon: () -> Unit,
    ): Outcome {
        copy()
        if (!windowOpen()) {
            abandon()
            return Outcome.DEFERRED
        }
        commit()
        return Outcome.COMMITTED
    }
}
