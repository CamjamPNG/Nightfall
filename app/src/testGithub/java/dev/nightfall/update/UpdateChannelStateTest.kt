// SPDX-License-Identifier: MIT
package dev.nightfall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelStateTest {
    private val now = 1_700_000_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun theChannelAsksGitHubAtMostOnceEverySixHours() {
        assertTrue(CheckThrottle.allows(now, 0L))
        assertTrue(CheckThrottle.allows(now, now - CheckThrottle.INTERVAL_MS))
        assertFalse(CheckThrottle.allows(now, now - CheckThrottle.INTERVAL_MS + 1))
        assertFalse(CheckThrottle.allows(now, now - hour))
        assertFalse("a clock that moves backwards must not open the gate", CheckThrottle.allows(now, now + hour))
        assertEquals(0, CheckThrottle.minutesSince(now, now + hour))
        assertEquals(90, CheckThrottle.minutesSince(now, now - 90 * 60_000L))
        assertEquals(0, CheckThrottle.minutesUntilAllowed(now, now - CheckThrottle.INTERVAL_MS))
        assertEquals(300, CheckThrottle.minutesUntilAllowed(now, now - hour))
        assertEquals(360, CheckThrottle.minutesUntilAllowed(now, now + hour))
    }

    @Test
    fun aManualCheckInsideTheIntervalIsNotARequest() {
        // What check(manual = true) reports when the gate is shut and nothing is known yet.
        val state = UpdateState.RecentCheck(CheckThrottle.minutesSince(now, now - hour))
        assertEquals(60, state.minutesAgo)
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
        assertFalse(shouldShowBanner(UpdateState.RecentCheck(10), null))
    }
}
