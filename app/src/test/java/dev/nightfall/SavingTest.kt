// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saving while the last save is still being written.
 *
 * A state write debounces by 250 ms and then goes to storage on an IO thread. An edit that arrives
 * after that debounce — a slider dragged twice, a layer added and removed in a row — starts while the
 * previous write is still in flight, and the debounce cancels the earlier job to keep one write.
 * Cancelling a write that has already started is not a failed save: it finishes, the next edit is
 * written after it, and the newest state is what the app holds. Reporting it as a failure puts a
 * "Saving failed" banner in front of the user for doing something ordinary on a slow device.
 *
 * The write is held open on a barrier instead of raced against the disk, because the runtime takes
 * its writer as a parameter: this test knows the first write is still suspended when the second edit
 * cancels it, on any machine, under any load.
 */
@RunWith(AndroidJUnit4::class)
class SavingTest : PlaybackHarness() {
    @Test
    fun anEditArrivingWhileTheLastWriteIsStillBeingWrittenIsNotReportedAsAFailure() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val written = mutableListOf<String>()
        val runtime =
            NightfallRuntime(
                app,
                write = { text ->
                    written += text
                    entered.complete(Unit)
                    release.await()
                },
            )
        try {
            await { runtime.ready.value }

            compose.runOnIdle { runtime.mix { it.copy(name = "First") } }
            advanceClock(250)
            await { entered.isCompleted } // the first write is now suspended inside `write`
            compose.runOnIdle { runtime.mix { it.copy(name = "Second") } }
            release.complete(Unit)

            await { written.size == 2 }
            assertTrue(
                "the superseded write did not carry the state it was started for: ${written.first()}",
                written.first().contains("\"First\""),
            )
            assertTrue(
                "the write that followed did not carry the newest state: ${written.last()}",
                written.last().contains("\"Second\""),
            )
            assertEquals("Second", runtime.saved.value.current.name)
            assertNull(
                "a save superseded by the next edit was reported as a failure: " +
                    "${runtime.status.value.error}",
                runtime.status.value.error,
            )
        } finally {
            release.complete(Unit)
            runtime.scope.cancel()
        }
    }
}
