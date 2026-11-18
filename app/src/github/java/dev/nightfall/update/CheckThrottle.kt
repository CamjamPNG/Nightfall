// SPDX-License-Identifier: MIT
package dev.nightfall.update

/**
 * The channel asks GitHub at most once every six hours, whoever asked: the foreground hint and the
 * Settings button take the same path, and an attempt spends the interval even when it fails, so
 * pressing the button cannot turn into a loop of requests.
 */
object CheckThrottle {
    const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun allows(now: Long, lastAttempt: Long): Boolean = now - lastAttempt >= INTERVAL_MS

    fun minutesSince(now: Long, lastAttempt: Long): Int =
        ((now - lastAttempt) / 60_000L).coerceAtLeast(0L).toInt()

    fun minutesUntilAllowed(now: Long, lastAttempt: Long): Int =
        ((INTERVAL_MS - (now - lastAttempt)) / 60_000L).coerceIn(0L, INTERVAL_MS / 60_000L).toInt()
}
