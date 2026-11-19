// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The phrase box on Home, driven the way a person drives it: type, press Make mix, read the chips,
 * remove one, press Play this mix — and check the mix the app then holds is the mix the chips said.
 *
 * One scenario per class, because this repository runs one JVM per class: a JVM that has already
 * driven several activities drops a press silently, and a dropped press looks exactly like a screen
 * that does not work. What the parse itself must produce is asserted in `PhraseInterpreterTest` in
 * `core`; these classes only prove the screen passes it through.
 */
@Config(qualifiers = "w411dp-h914dp-420dpi")
abstract class DescribeMixHarness : PlaybackHarness() {

    private val field = hasContentDescription("Describe the mix you want")

    private fun scrollTo(matcher: SemanticsMatcher) =
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)

    protected fun onScreen(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    /** Presses a control through its own action rather than an injected touch. */
    protected fun press(matcher: SemanticsMatcher) {
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    protected fun pressText(text: String) = press(hasText(text) and hasClickAction())

    /** Types a phrase and presses Make mix, as a person does. */
    protected fun describe(phrase: String) {
        scrollTo(field)
        compose.onNode(field).performTextClearance()
        compose.onNode(field).performTextInput(phrase)
        pressText("Make mix")
    }
}

@RunWith(AndroidJUnit4::class)
class DescribeMixChipsTest : DescribeMixHarness() {

    @Test
    fun aPhraseBecomesEditableChipsAndTheMixTheyPromise() {
        describe("heavy rain and a fan")
        assertTrue("the rain chip is missing", onScreen("Heavy rain · 71%"))
        assertTrue("the fan chip is missing", onScreen("Box fan · 50%"))
        assertTrue("the play action is missing", onScreen("Play this mix"))

        // The fan is not what this person wants after all: the chip comes off before anything plays.
        press(hasText("Box fan", substring = true) and hasClickAction())
        assertTrue("the removed chip is still on screen", !onScreen("Box fan · 50%"))
        assertTrue("removing a chip removed its sibling too", onScreen("Heavy rain · 71%"))

        pressText("Play this mix")
        await { runtime.saved.value.current.layers.size == 1 }
        val layer = runtime.saved.value.current.layers.single()
        org.junit.Assert.assertEquals("heavy-rain", layer.soundId)
        org.junit.Assert.assertEquals(.71f, layer.gain, 1e-4f)
        org.junit.Assert.assertEquals(
            "the mix keeps the words it came from",
            "Heavy rain and a fan",
            runtime.saved.value.current.name,
        )
        org.junit.Assert.assertNull(runtime.status.value.error)
    }
}

@RunWith(AndroidJUnit4::class)
class DescribeMixTimerTest : DescribeMixHarness() {

    @Test
    fun aPhraseThatNamesATimerSetsIt() {
        describe("rain for 45 minutes")
        assertTrue("the named timer is not on the card", onScreen("Sleep timer · 45 min"))

        pressText("Play this mix")
        await { runtime.timer.active }
        val remaining = runtime.timer.remainingMs(android.os.SystemClock.elapsedRealtime())
        assertTrue(
            "the runtime holds $remaining ms, not the 45 minutes the phrase named",
            remaining in 44L * 60_000..45L * 60_000,
        )
        assertTrue("the timer did not reach the status the UI reads", runtime.status.value.timerActive)
    }
}

@RunWith(AndroidJUnit4::class)
class DescribeMixEmptyTest : DescribeMixHarness() {

    @Test
    fun nothingRecognisableSaysSoAndOffersTheLibrary() {
        describe("zzzz qqqq")
        assertTrue(
            "the empty phrase did not say it understood nothing",
            onScreen("find a sound in that", substring = true),
        )
        assertTrue("an empty phrase still offered a mix to play", !onScreen("Play this mix"))

        pressText("Browse the library")
        assertTrue("the library did not open", onScreen("Sound library"))
    }
}

@RunWith(AndroidJUnit4::class)
class DescribeMixConflictTest : DescribeMixHarness() {

    @Test
    fun aContradictionIsExplainedRatherThanGuessed() {
        describe("loud rain, silent")
        assertTrue("the conflict was not explained", onScreen("can’t both be true", substring = true))
        assertTrue("the conflict did not say what contradicted", onScreen("loud", substring = true))
        assertTrue("the conflict did not say what contradicted", onScreen("silent", substring = true))
        assertTrue("a contradictory phrase still offered a mix", !onScreen("Play this mix"))
    }
}
