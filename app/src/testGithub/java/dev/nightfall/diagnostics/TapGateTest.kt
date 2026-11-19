// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reveal gesture as arithmetic: five taps inside a window that starts at the first tap.
 *
 * The clock is handed in, so "ten seconds later" is exact rather than a race with the test runner.
 */
class TapGateTest {

    @Test
    fun fourTapsNeverOpen() {
        val gate = TapGate()
        repeat(4) { index -> assertFalse(gate.tap(1_000L + index * 100)) }
    }

    @Test
    fun fiveTapsInsideThreeSecondsOpen() {
        val gate = TapGate()
        val times = listOf(0L, 400L, 900L, 2_100L, 2_999L)
        val opened = times.map { gate.tap(it) }
        assertFalse("the gesture opened before the fifth tap", opened.take(4).any { it })
        assertTrue("five taps in three seconds did not open the surface", opened.last())
    }

    @Test
    fun fiveTapsOverTenSecondsNeverOpen() {
        val gate = TapGate()
        val opened = listOf(0L, 2_500L, 5_000L, 7_500L, 10_000L).map { gate.tap(it) }
        assertFalse("taps spread over ten seconds opened the surface", opened.any { it })
    }

    @Test
    fun theWindowIsThreeSecondsAndNotLonger() {
        val gate = TapGate()
        assertFalse(gate.tap(0))
        assertFalse(gate.tap(1_000))
        assertFalse("a tap past the three-second window continued the run", gate.tap(3_001))
        assertFalse(gate.tap(3_100))
        assertFalse(gate.tap(3_200))
        assertFalse("three taps after the restart is not five", gate.tap(3_300))
        assertTrue("the remainder of the interrupted run was not counted from its restart", gate.tap(3_400))
    }

    @Test
    fun aCompletedRunStartsTheNextOneFromNothing() {
        val gate = TapGate()
        repeat(5) { gate.tap(it * 100L) }
        assertFalse("the sixth tap continued the run that already opened", gate.tap(500))
    }
}
