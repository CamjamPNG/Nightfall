// SPDX-License-Identifier: MIT
package dev.nightfall.render

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phrase box after it has understood something: the words came back as chips, each carrying the
 * level the phrase's own intensity words chose, with the timer the phrase named.
 *
 * Two frames, dark then light, of the same state — the card is the app's own composition behind the
 * real activity, and the phrases are the brief's "rain on a tin roof with a distant rumble".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class DescribeMixRenderTest : ScreenshotHarness() {

    @Test
    fun thePhraseBoxShowsWhatItUnderstoodInBothThemes() {
        compose
            .onNodeWithContentDescription("Describe the mix you want")
            .performTextInput("rain on a tin roof with a distant rumble for 45 minutes")
        compose
            .onNode(hasText("Make mix") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.waitUntil(WAIT_MS) { onScreen("Rain on metal roof · 50%") }
        assertTrue("the distant rumble is missing", onScreen("Distant thunder · 35%"))
        assertTrue("the named timer is missing", onScreen("Sleep timer · 45 min"))
        assertTrue("the play action is missing", onScreen("Play this mix"))
        capture("16-describe-dark.png")

        compose.runOnIdle {
            runtime.change { it.copy(settings = it.settings.copy(light = true)) }
        }
        compose.waitForIdle()
        assertTrue("the card left the light frame", onScreen("THIS IS WHAT I HEARD"))
        capture("17-describe-light.png")

        // Back to dark for anything this JVM renders after this class, and so the setting the
        // harness captured is not the one a later scenario restores.
        compose.runOnIdle { runtime.change { it.copy(settings = it.settings.copy(light = false)) } }
    }
}
