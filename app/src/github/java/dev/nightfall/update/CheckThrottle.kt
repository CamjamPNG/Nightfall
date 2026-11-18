// SPDX-License-Identifier: MIT
package dev.nightfall.update

/**
 * Two rules, and they are different promises.
 *
 * - Every request — the foreground hint and the Settings button alike — waits at least
 *   [FLOOR_MS] since the previous attempt. The attempt is recorded before the request, so a
 *   fetch or parse that fails counts too and nothing can be hammered.
 * - The automatic check the app makes for itself happens at most once every [INTERVAL_MS],
 *   measured from the last *successful* answer, so a failed attempt never spends that window.
 */
object CheckThrottle {
    const val FLOOR_MS = 15 * 60 * 1000L
    const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** May any caller make a request now? A stored time in the future means the clock was reset. */
    fun allowsRequest(now: Long, lastAttempt: Long): Boolean =
        now < lastAttempt || now - lastAttempt >= FLOOR_MS

    /** May the app ask on its own? Requires a successful answer to be older than the interval. */
    fun allowsAutomatic(now: Long, lastSuccess: Long): Boolean =
        now < lastSuccess || now - lastSuccess >= INTERVAL_MS

    fun minutesSince(now: Long, then: Long): Int =
        ((now - then) / 60_000L).coerceIn(0L, INTERVAL_MS / 60_000L).toInt()

    fun minutesUntilRequest(now: Long, lastAttempt: Long): Int =
        if (now < lastAttempt) 0
        else ((FLOOR_MS - (now - lastAttempt)) / 60_000L).coerceIn(0L, FLOOR_MS / 60_000L).toInt()
}
