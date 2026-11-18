// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A fresh runtime reads the mix and settings back from local storage.
 *
 * Nightfall debounces its state write by 250 ms of main-clock time and then writes through DataStore
 * on an IO thread, so this polls while letting virtual time pass instead of sleeping. The file check
 * proves the values reached the disk; the second runtime proves they decode back, which is the path
 * that matters after a process restart.
 */
@RunWith(AndroidJUnit4::class)
class StateRestorationTest : PlaybackHarness() {
    @Test
    fun settingsAndMixerStateRestoreFromLocalStorage() {
        val firstLayer = runtime.saved.value.current.layers.first().id
        compose.runOnIdle {
            runtime.layer(firstLayer) { it.copy(gain = .27f, pan = -.2f) }
            runtime.change { it.copy(settings = it.settings.copy(reducedMotion = true)) }
        }

        val storedFile = {
            app.filesDir.walkTopDown().firstOrNull { it.name == "nightfall.preferences_pb" }
        }
        await { (storedFile()?.length() ?: 0L) > 0 }
        val written = String(checkNotNull(storedFile()).readBytes(), Charsets.ISO_8859_1)
        assertTrue("the gain never reached local storage", written.contains("0.27"))
        assertTrue("reduced motion never reached local storage", written.contains("reducedMotion"))

        val restored = NightfallRuntime(app)
        await { restored.ready.value }
        val layer = restored.saved.value.current.layers.first { it.id == firstLayer }
        assertEquals(.27f, layer.gain, 0f)
        assertEquals(-.2f, layer.pan, 0f)
        assertTrue(restored.saved.value.settings.reducedMotion)
        assertEquals(9, restored.saved.value.current.layers.size)
        restored.scope.cancel()
    }
}
