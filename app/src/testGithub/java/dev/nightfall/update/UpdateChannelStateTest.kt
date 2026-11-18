// SPDX-License-Identifier: MIT
package dev.nightfall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelStateTest {
    private val now = 1_700_000_000_000L
    private val minute = 60 * 1000L
    private val hour = 60 * minute

    @Test
    fun everyRequestWaitsFifteenMinutesSinceTheLastAttempt() {
        assertTrue(CheckThrottle.allowsRequest(now, 0L))
        assertTrue(CheckThrottle.allowsRequest(now, now - CheckThrottle.FLOOR_MS))
        assertFalse(CheckThrottle.allowsRequest(now, now - CheckThrottle.FLOOR_MS + 1))
        assertFalse("a failed attempt a moment ago still counts", CheckThrottle.allowsRequest(now, now - minute))
        // A stored time in the future means the clock moved; treat it as expired, the test below.
        assertTrue(CheckThrottle.allowsRequest(now, now + hour))
        assertEquals(0, CheckThrottle.minutesSince(now, now + hour))
        assertEquals(90, CheckThrottle.minutesSince(now, now - 90 * minute))
        assertEquals(0, CheckThrottle.minutesUntilRequest(now, now - CheckThrottle.FLOOR_MS))
        assertEquals(10, CheckThrottle.minutesUntilRequest(now, now - 5 * minute))
        assertEquals("a future timestamp is expired, not a wait", 0, CheckThrottle.minutesUntilRequest(now, now + hour))
    }

    @Test
    fun aCorrectedClockDoesNotLockTheChannel() {
        // The attempt was recorded while the device clock was ahead by two hours.
        assertTrue(CheckThrottle.allowsRequest(now, now + 2 * hour))
        assertTrue(CheckThrottle.allowsAutomatic(now, now + 2 * hour))
        assertEquals(0, CheckThrottle.minutesUntilRequest(now, now + 2 * hour))
    }

    @Test
    fun theAppsOwnCheckWaitsSixHoursForAnAnswer() {
        assertTrue(CheckThrottle.allowsAutomatic(now, 0L))
        assertTrue(CheckThrottle.allowsAutomatic(now, now - CheckThrottle.INTERVAL_MS))
        assertFalse(CheckThrottle.allowsAutomatic(now, now - CheckThrottle.INTERVAL_MS + 1))
        // A failed attempt is recorded as an attempt but never as a success, so the app's own
        // check is allowed again once the fifteen-minute floor has passed.
        val lastSuccess = now - 6 * hour - 1
        assertTrue(CheckThrottle.allowsAutomatic(now, lastSuccess))
        assertTrue(CheckThrottle.allowsRequest(now, now - CheckThrottle.FLOOR_MS))
    }

    @Test
    fun aCheckInsideTheFloorReportsWhenToTryAgain() {
        val state =
            UpdateState.RecentCheck(
                CheckThrottle.minutesSince(now, now - 5 * minute),
                CheckThrottle.minutesUntilRequest(now, now - 5 * minute),
            )
        assertEquals(5, state.minutesAgo)
        assertEquals(10, state.retryInMinutes)
    }

    @Test
    fun hidingTheBannerKeepsTheUpdateReachable() {
        val available = UpdateState.Available("1.0.2", 5, 91_097_092)
        assertTrue(shouldShowBanner(available, null))
        assertFalse("a hidden release stops nagging", shouldShowBanner(available, "1.0.2"))
        assertTrue("a different release brings the banner back", shouldShowBanner(available, "1.0.1.2"))
        assertTrue(shouldShowBanner(UpdateState.Downloading("1.0.2", 0.4f), "1.0.2"))
        assertTrue(shouldShowBanner(UpdateState.Installing("1.0.2"), "1.0.2"))
        assertTrue(shouldShowBanner(UpdateState.Failed("refused"), "1.0.2"))
        assertFalse(shouldShowBanner(UpdateState.Idle, null))
        assertFalse(shouldShowBanner(UpdateState.UpToDate("1.0.1.2"), null))
        assertFalse(shouldShowBanner(UpdateState.RecentCheck(10, 5), null))
    }
}
