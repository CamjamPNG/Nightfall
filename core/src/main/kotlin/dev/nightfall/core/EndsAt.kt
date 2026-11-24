// SPDX-License-Identifier: MIT
package dev.nightfall.core

/**
 * A sleep timer chosen by the clock rather than by a number of minutes: "until 6:30".
 *
 * A chosen time becomes the same countdown every duration uses — whole minutes, run by [SleepTimer]
 * against monotonic time — and nothing here re-reads the clock while it runs. That is the honest
 * shape. A countdown is a duration: the night lasts the number of minutes it was given, so a clock
 * that jumps mid-night (a daylight-saving change, a manual correction) moves the wall time the timer
 * lands on and not the time it runs. A time of day is only how the night was chosen.
 */
object EndsAt {
    /** Minutes in a day: what a chosen time is measured within, and the largest countdown offered. */
    const val MINUTES_PER_DAY = 1440

    /**
     * The three times the sheet offers beside its picker, in minutes of the day: half past six,
     * seven and eight. They are when a night ends, and they are the same three at every hour — a
     * list that moved with the clock would make a person read it instead of recognise it.
     */
    val SUGGESTIONS = listOf(6 * 60 + 30, 7 * 60, 8 * 60)

    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = MINUTE_MS * MINUTES_PER_DAY

    /**
     * The countdown that reaches [targetMinuteOfDay] from [nowMillisOfDay], in whole minutes.
     *
     * Three rules, each of which a night depends on:
     *
     * - A time earlier than now is tomorrow. "Until 6:30" at two in the morning is four and a half
     *   hours; the same words at eight in the morning are the next morning, which is what someone
     *   going to bed at eight is asking for.
     * - A time in this very minute is one minute, not a day. "Until now" is a night already over,
     *   and the 24 hours a roll-over would give is a different request from the one made.
     * - Minutes round *up*, so "until 6:30" never stops at 6:29:30. The countdown is therefore never
     *   shorter than the time asked for, and never more than a minute longer.
     */
    fun minutesUntil(nowMillisOfDay: Long, targetMinuteOfDay: Int): Int {
        require(targetMinuteOfDay in 0 until MINUTES_PER_DAY) {
            "Not a minute of the day: $targetMinuteOfDay"
        }
        val now = nowMillisOfDay.mod(DAY_MS)
        val target = targetMinuteOfDay.toLong() * MINUTE_MS
        val ahead = if (target < now) target + DAY_MS - now else target - now
        if (ahead == 0L) return 1
        return ((ahead + MINUTE_MS - 1) / MINUTE_MS).toInt()
    }
}
