// SPDX-License-Identifier: MIT
package dev.nightfall.update

/**
 * What the app knows about audio right now. [UNKNOWN] is a foreground entry that arrived before the
 * app has reported a playback fact; it exists so the install guard can refuse on "no answer yet"
 * rather than guess "not playing".
 */
enum class Playback {
    PLAYING,
    STOPPED,
    UNKNOWN,
}

/**
 * The automatic channel's rules, as functions of facts. Pure Kotlin with no Android types, so the
 * one rule that must never regress — never install into playing audio — is tested rather than
 * trusted, together with the pacing that keeps the check from becoming hammering.
 */
object UpdateAutomation {
    /** The GitHub channel's own default: an owner who installs from GitHub wants the updates. */
    const val AUTOMATIC_BY_DEFAULT = true

    /**
     * May a downloaded, verified update be handed to Android now without anyone asking?
     *
     * An install kills the process, so an unattended install — the one the app starts itself, on a
     * foreground entry or when playback stops — never happens while audio is playing: at three in
     * the morning that would silence the app people leave running all night. [Playback.UNKNOWN]
     * refuses too, so a race between a foreground entry and the first playback report can only
     * postpone an install and never interrupt one. A tap on Update is the user asking, and the
     * guard does not apply to it.
     */
    fun mayInstallNow(automatic: Boolean, playback: Playback): Boolean =
        automatic && playback == Playback.STOPPED

    /**
     * The commit-time re-read of the same rule. Copying the APK takes seconds with Play still live,
     * so the window is sampled again immediately before Android commits: playback that resumed
     * during the copy defers the install. [guarded] is false only for a tap the owner made.
     */
    fun mayCommitNow(guarded: Boolean, automatic: Boolean, playback: Playback): Boolean =
        !guarded || mayInstallNow(automatic, playback)

    /**
     * May the app ask GitHub on its own? The setting gates it, a connection is required, and both
     * pacing rules apply: the fifteen-minute floor shared by every request, and six hours since the
     * last successful answer. A failed attempt is recorded as an attempt only, so it never starts
     * that window.
     */
    fun wantsAutomaticCheck(
        automatic: Boolean,
        now: Long,
        lastAttempt: Long,
        lastSuccess: Long,
        online: Boolean,
    ): Boolean =
        automatic &&
            online &&
            CheckThrottle.allowsRequest(now, lastAttempt) &&
            CheckThrottle.allowsAutomatic(now, lastSuccess)

    /**
     * Does a download from an earlier session still stand? A process Android killed mid-install must
     * find its verified file waiting, but never one for a version this build already runs.
     */
    fun resumesAfterRestart(pendingVersionCode: Int, installedVersionCode: Int, filePresent: Boolean): Boolean =
        filePresent && pendingVersionCode > installedVersionCode
}
