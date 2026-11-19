// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A night is only measured while collection was on.
 *
 * The switch is part of the session stream rather than a filter applied to its result, because a
 * filter cannot express this: a four-hour night that was watched for the last ten minutes must be
 * recorded as ten minutes, and a night that ran entirely while the switch was off must not be
 * recorded at all. `reset` is the switch being turned off, and it abandons the run rather than
 * pausing it.
 */
class SessionClockTest {
    private var now = 0L
    private val clock = SessionClock { now }

    @Test
    fun aSessionIsMeasuredFromStartToStop() {
        now = 1_000
        assertNull(clock.playing(true))
        now = 1_000 + 40 * 60_000L
        assertEquals(40 * 60_000L, clock.playing(false))
    }

    @Test
    fun aSessionThatRanWhileCollectionWasOffIsNotRecorded() {
        now = 0
        clock.reset()
        now = 2 * 3_600_000L
        assertNull("a stop with no consented start reported a length", clock.playing(false))
    }

    @Test
    fun turningCollectionOffAbandonsTheRunRatherThanPausingIt() {
        now = 0
        assertNull(clock.playing(true))
        now = 2 * 3_600_000L
        clock.reset()
        now = 2 * 3_600_000L + 10 * 60_000L
        assertNull("re-enabling mid-night reported the session that predated it", clock.playing(true))
        now = 2 * 3_600_000L + 25 * 60_000L
        assertEquals(
            "the session was measured from before re-enabling",
            15 * 60_000L,
            clock.playing(false),
        )
    }

    @Test
    fun oneSessionIsRecordedOnce() {
        now = 0
        clock.playing(true)
        now = 60_000L
        assertEquals(60_000L, clock.playing(false))
        now = 120_000L
        assertNull("the same session was reported twice", clock.playing(false))
    }
}
