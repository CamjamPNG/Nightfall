// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saving while the last save is still being written.
 *
 * A state write debounces by 250 ms and then goes through DataStore on an IO thread. An edit that
 * arrives after that debounce — a slider dragged twice, a layer added and removed in a row — starts
 * while the previous write is still in flight, and the debounce cancels the earlier job to keep one
 * write. Cancelling a write that has already started is not a failed save: the newer edit is written
 * after it and the newest state is what lands on disk. Reporting it as a failure would put a "Saving
 * failed" banner in front of the user for doing something ordinary on a slow device.
 */
@RunWith(AndroidJUnit4::class)
class SavingTest : PlaybackHarness() {
    @Test
    fun anEditArrivingWhileTheLastWriteIsStillBeingWrittenIsNotReportedAsAFailure() {
        compose.runOnIdle { runtime.mix { it.copy(name = "First") } }
        // The debounce has now expired, so the first write is inside DataStore and in flight.
        advanceClock(250)
        compose.runOnIdle { runtime.mix { it.copy(name = "Second") } }

        awaitSaved()

        assertEquals("Second", runtime.saved.value.current.name)
        assertNull(
            "a save superseded by the next edit was reported as a failure: " +
                "${runtime.status.value.error}",
            runtime.status.value.error,
        )
    }
}
