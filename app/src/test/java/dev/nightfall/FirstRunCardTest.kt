// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The first-run card: one Home card a new person sees once, with three ways in, that is never seen
 * again — dismissed, used, or overtaken by the first night they play.
 *
 * One scenario per class, because each one changes the persisted answer and this repository runs one
 * JVM per class (see the comment on `unitTests.all { it.forkEvery = 1 }`).
 */
@Config(qualifiers = "w411dp-h914dp-420dpi")
abstract class FirstRunHarness : PlaybackHarness() {

    protected fun onScreen(text: String) =
        compose.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .any { it.layoutInfo.isPlaced }

    /** Presses a row through its own action, the way the screen exposes it. */
    protected fun press(text: String) {
        compose.onNode(hasText(text) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /**
     * The answer the next launch would get: a second runtime over the same storage, which is what
     * the app builds when the process starts again. The flag has to be on disk for this to be true.
     */
    protected fun nextLaunchFirstRunDone(): Boolean {
        val next = NightfallRuntime(app)
        compose.waitUntil(WAIT_MS) { next.ready.value }
        val done = next.firstRunDone.value
        next.scope.cancel()
        return done
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardFreshInstallTest : FirstRunHarness() {
    @Test
    fun aFreshInstallShowsAllThreeWaysIn() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        assertTrue("the presets row is missing", onScreen("Pick a ready-made night"))
        assertTrue("the phrase row is missing", onScreen("Type what you want"))
        assertTrue("the library row is missing", onScreen("Browse everything"))
        assertTrue("there is no way to dismiss it", onScreen("Not now"))
        assertFalse("a fresh install is already done with the card", runtime.firstRunDone.value)
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardDismissTest : FirstRunHarness() {
    @Test
    fun dismissingItKeepsItAway() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        press("Not now")
        assertFalse("the card survived its own dismiss", onScreen("Three ways in"))
        // Not just hidden in this composition: the next launch reads the answer from storage.
        assertTrue("the dismissal never reached storage", nextLaunchFirstRunDone())
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardFirstPlayTest : FirstRunHarness() {
    @Test
    fun playingOneNightRetiresItAndTheCardNeverBlocksPlayback() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        // The transport is reachable with the card up: the card sits under it, never over it.
        clickTransport("Play soundscape")
        deliverStartCommand()
        await { runtime.status.value.playing }
        compose.waitUntil(WAIT_MS) { !onScreen("Three ways in") }
        assertFalse("the card stayed up over a running mix", onScreen("Three ways in"))
        assertTrue("the first night never reached storage", nextLaunchFirstRunDone())
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardSavedMixTest : FirstRunHarness() {
    @Test
    fun someoneWithASavedMixIsNotNew() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        compose.runOnIdle {
            runtime.savePreset(runtime.saved.value.current.copy(name = "Kept night"))
        }
        compose.waitUntil(WAIT_MS) { !onScreen("Three ways in") }
        assertFalse("a person with a saved mix was greeted as new", onScreen("Three ways in"))
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardUpgradeTest : FirstRunHarness() {
    @Test
    fun storageWrittenBeforeTheCardExistedIsNotANewPerson() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        // A document with no first-run answer in it, which is exactly what an older build leaves.
        compose.runOnIdle { runtime.change { it.copy(current = it.current.copy(name = "Older")) } }
        awaitSaved()
        assertTrue("an upgrade would be greeted as a new person", nextLaunchFirstRunDone())
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardPresetsEntryTest : FirstRunHarness() {
    @Test
    fun thePresetsRowOpensPresets() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        press("Pick a ready-made night")
        compose.waitUntil(WAIT_MS) { onScreen("Find your night") }
        assertTrue("the Presets tab did not open", onScreen("Find your night"))
        assertFalse("the card came back on the way", onScreen("Three ways in"))
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardLibraryEntryTest : FirstRunHarness() {
    @Test
    fun theLibraryRowOpensTheLibrary() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        press("Browse everything")
        compose.waitUntil(WAIT_MS) { onScreen("Sound library") }
        assertTrue("the Library tab did not open", onScreen("Sound library"))
        assertFalse("the card came back on the way", onScreen("Three ways in"))
    }
}

@RunWith(AndroidJUnit4::class)
class FirstRunCardDescribeEntryTest : FirstRunHarness() {
    @Test
    fun thePhraseRowPutsTheCursorInThePhraseBox() {
        compose.waitUntil(WAIT_MS) { onScreen("Three ways in") }
        press("Type what you want")
        compose.waitUntil(WAIT_MS) { !onScreen("Three ways in") }
        compose
            .onNodeWithContentDescription("Describe the mix you want")
            .assertIsFocused()
    }
}
