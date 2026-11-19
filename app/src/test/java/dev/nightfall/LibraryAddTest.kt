// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.Library
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A Library row still adds its sound in one tap, and the one entry no recording is bundled for still
 * says so instead of adding silence.
 *
 * Its own class, so its own JVM, because it is the only Library scenario that writes the runtime's
 * persisted state: this repository runs one JVM per test class for exactly that reason.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class LibraryAddTest : PlaybackHarness() {

    @Test
    fun aRowAddsInOneTapAndAnUnavailableEntrySaysSo() {
        compose.onNodeWithText("Library", useUnmergedTree = true).performClick()
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText("Sound library", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.layoutInfo.isPlaced }
        }
        compose.waitForIdle()

        val before = runtime.saved.value.current.layers.count { it.soundId == "white" }
        compose.onNodeWithContentDescription("Add White")
            .performSemanticsAction(SemanticsActions.OnClick)
        await { runtime.saved.value.current.layers.count { it.soundId == "white" } == before + 1 }

        val unavailable = Library.all.first { !it.available }
        compose.onNodeWithContentDescription("Search sounds").performTextInput(unavailable.name)
        compose.waitForIdle()
        assertTrue(
            "the unavailable entry does not say so",
            compose.onAllNodesWithText("Recording not bundled", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { it.layoutInfo.isPlaced },
        )
        compose.onNodeWithContentDescription("Add ${unavailable.name}").assertIsNotEnabled()
    }
}
