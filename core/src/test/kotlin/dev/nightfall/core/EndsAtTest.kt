// SPDX-License-Identifier: MIT
package dev.nightfall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The conversion behind "until 6:30", as arithmetic: which day the time is on, how the minutes
 * round, and what the countdown is never allowed to do — stop early, or run past a minute of the
 * time it was given.
 *
 * Nothing here reads a clock. The arithmetic is the whole of the rule; the app supplies the phone's
 * time of day and runs the minutes it returns through the same countdown a duration uses.
 */
class EndsAtTest {

    /** A time of day as milliseconds since midnight. */
    private fun at(hour: Int, minute: Int, second: Int = 0, millisecond: Int = 0): Long =
        ((hour * 60L + minute) * 60 + second) * 1_000 + millisecond

    @Test
    fun aTimeLaterTodayIsTheMinutesBetween() {
        assertEquals("6:00 until 6:30 is half an hour", 30, EndsAt.minutesUntil(at(6, 0), 6 * 60 + 30))
        assertEquals("6:00 until 8:00 is two hours", 120, EndsAt.minutesUntil(at(6, 0), 8 * 60))
        assertEquals("21:15 until 23:00 is 105 minutes", 105, EndsAt.minutesUntil(at(21, 15), 23 * 60))
    }

    @Test
    fun aTimeThatHasGoneIsTomorrow() {
        assertEquals("23:00 until 6:30 is tomorrow's 6:30", 450, EndsAt.minutesUntil(at(23, 0), 6 * 60 + 30))
        assertEquals("one minute past the time is tomorrow, less that minute", 1409, EndsAt.minutesUntil(at(6, 31), 6 * 60))
        assertEquals("midnight is tomorrow when it is now nine", 180, EndsAt.minutesUntil(at(21, 0), 0))
    }

    @Test
    fun aTimeInThisVeryMinuteIsTheMinimumNotADay() {
        // Exactly now is a night that is already over. One minute is the honest countdown; the 24
        // hours a roll-over would produce is a different request from the one the person made.
        assertEquals(1, EndsAt.minutesUntil(at(6, 30), 6 * 60 + 30))
        assertEquals("a second into the minute is tomorrow, not a minute", 1440, EndsAt.minutesUntil(at(6, 30, 1), 6 * 60 + 30))
    }

    @Test
    fun minutesRoundUp() {
        assertEquals("one second past the half hour still gets the whole minute", 30, EndsAt.minutesUntil(at(6, 0, 1), 6 * 60 + 30))
        assertEquals("thirty seconds short rounds up to one", 1, EndsAt.minutesUntil(at(6, 29, 30), 6 * 60 + 30))
        assertEquals("one second short rounds up to one", 1, EndsAt.minutesUntil(at(6, 29, 59), 6 * 60 + 30))
        assertEquals("a second short of two minutes rounds up to two", 2, EndsAt.minutesUntil(at(6, 28, 1), 6 * 60 + 30))
        assertEquals("a millisecond past is a whole extra minute", 31, EndsAt.minutesUntil(at(5, 59, 59, 999), 6 * 60 + 30))
    }

    @Test
    fun theCountdownNeverStopsEarlyAndNeverRunsPastAMinute() {
        // The property the rounding exists for, over every pairing that can be asked for at a real
        // moment: a timer asked for 6:30 must not end at 6:29:30, and must not keep a person up
        // waiting for a minute that is not theirs.
        for (nowMinute in 0 until EndsAt.MINUTES_PER_DAY step 7) {
            for (targetMinute in 0 until EndsAt.MINUTES_PER_DAY step 13) {
                val now = nowMinute * 60_000L + 17_000L
                val minutes = EndsAt.minutesUntil(now, targetMinute)
                val end = now + minutes * 60_000L
                val target = targetMinute * 60_000L
                val landing = if (target < now) target + 1440 * 60_000L else target
                assertTrue(
                    "a $targetMinute-minute time from $nowMinute ended $minutes minutes early",
                    end >= landing,
                )
                assertTrue(
                    "a $targetMinute-minute time from $nowMinute ran ${end - landing}ms past",
                    end - landing <= 60_000L,
                )
                assertTrue("$minutes minutes is not a countdown", minutes in 1..EndsAt.MINUTES_PER_DAY)
            }
        }
    }

    @Test
    fun theSuggestionsAreThreeAscendingTimesOfTheDay() {
        val suggestions = EndsAt.SUGGESTIONS
        assertEquals("the sheet offers three times", 3, suggestions.size)
        assertEquals("the sheet offers 6:30, 7:00 and 8:00", listOf(390, 420, 480), suggestions)
        assertTrue("the times are not in the order the sheet draws them", suggestions == suggestions.sorted())
        assertTrue(
            "a suggestion is not a time of day",
            suggestions.all { it in 0 until EndsAt.MINUTES_PER_DAY },
        )
    }

    @Test
    fun aTimeThatIsNotOnTheClockIsRefused() {
        for (minute in listOf(-1, EndsAt.MINUTES_PER_DAY, 10_000)) {
            try {
                EndsAt.minutesUntil(at(6, 0), minute)
                fail("$minute was accepted as a minute of the day")
            } catch (expected: IllegalArgumentException) {
                // The picker cannot produce one, and a document can say anything.
            }
        }
    }
}
