// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is handed, surface by surface: the name of every control, the state it is in,
 * the actions it offers, and how big it is to a finger.
 *
 * These read the semantics tree Compose builds — the same tree TalkBack walks — so they are evidence
 * about what TalkBack is *offered*. They are not evidence about what it says out loud: a JVM cannot
 * tell you whether a sentence lands in a dark room, and `docs/ACCESSIBILITY.md` keeps that gap open.
 *
 * One scenario per class, because this repository runs one JVM per class (`forkEvery = 1`): a JVM
 * that has already driven one activity drops a later interaction silently, and a dropped interaction
 * looks exactly like a screen that does not work.
 */
@Config(qualifiers = "w411dp-h914dp-420dpi")
abstract class AccessibilityHarness : PlaybackHarness() {

    protected fun controls(): List<SemanticsNode> =
        compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()

    /** The words a node is announced with: its description, else its text. */
    protected fun spoken(node: SemanticsNode): String? {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        if (!description.isNullOrBlank()) return description
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        return text?.takeIf { it.isNotBlank() }
    }

    /** A node's own size in dp, at the density this screen is drawn at. */
    protected fun dp(node: SemanticsNode): Pair<Float, Float> {
        val scale = compose.density.density
        return (node.size.width / scale).toFloat() to (node.size.height / scale).toFloat()
    }

    /**
     * A finger hits the *touch* bounds, which a component may expand past the box it draws. Measuring
     * the layout box instead would report a 40dp icon button whose hit area is already 48, and would
     * miss the reverse: a 40dp box that is the whole target.
     */
    protected fun hit(node: SemanticsNode): Pair<Float, Float> {
        val scale = compose.density.density
        return (node.touchBoundsInRoot.width / scale).toFloat() to
            (node.touchBoundsInRoot.height / scale).toFloat()
    }

    /**
     * Every control a finger has to hit, judged on the touch bounds — the area Android's own
     * accessibility service reports and inflates to the minimum, not the box a component draws.
     *
     * A node cut off by the viewport reports a short touch bound for a reason that is not about the
     * control, so a clipped node is measured by scrolling tests instead of here.
     */
    protected fun undersized(): List<String> = controls().mapNotNull { node ->
        val clipped =
            node.boundsInRoot.height + .5f < node.size.height ||
                node.boundsInRoot.width + .5f < node.size.width
        if (clipped) return@mapNotNull null
        val (w, h) = hit(node)
        if (w < 48f || h < 48f)
            "%.1f x %.1f dp (drawn %.1f x %.1f): %s".format(
                w, h, dp(node).first, dp(node).second, spoken(node) ?: "(no name)")
        else null
    }

    /** A control with nothing to say is an "unlabeled button" to a screen reader. */
    protected fun unnamed(): List<String> = controls().mapNotNull { node ->
        val (w, h) = dp(node)
        if (spoken(node) == null) "%.1f x %.1f dp node with no label".format(w, h) else null
    }

    protected fun nodeOf(description: String): SemanticsNode =
        compose.onAllNodesWithContentDescription(description).onFirst().fetchSemanticsNode()

    protected fun stateOf(description: String): String? = nodeOf(description).let {
        it.config.getOrNull(SemanticsProperties.StateDescription)
    }

    protected fun customActions(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label } ?: emptyList()

    protected fun press(description: String) {
        compose.onAllNodesWithContentDescription(description)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Opens a tab the way a person does: through the navigation item's own label. */
    protected fun open(tab: String) {
        compose.onNode(hasText(tab) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    protected fun scrollTo(matcher: SemanticsMatcher) =
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)

    protected fun nodeWithText(text: String): SemanticsNode =
        compose.onNode(hasText(text) and hasClickAction()).fetchSemanticsNode()

    /** Every polite live region on screen, with the words it carries. */
    protected fun liveRegions(): List<String> =
        compose
            .onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .mapNotNull { spoken(it) }

    /** The layer these classes drive, named the way its own row names it. */
    protected fun layerName(soundId: String): String =
        runtime.saved.value.current.layers.first { it.soundId == soundId }.name
}

@RunWith(AndroidJUnit4::class)
class TransportStateTest : AccessibilityHarness() {
    @Test
    fun thePlayControlSaysWhatItWillDoAndWhatIsHappening() {
        assertEquals("a silent transport does not say it is paused", "Paused", stateOf("Play soundscape"))
        press("Play soundscape")
        // The press asks the OS for audio; the playing state arrives when the service is told to start.
        deliverStartCommand()
        await { runtime.status.value.playing }
        compose.waitUntil(WAIT_MS) { stateOf("Pause soundscape") == "Playing" }
        assertEquals("a running mix does not say so", "Playing", stateOf("Pause soundscape"))
    }
}

@RunWith(AndroidJUnit4::class)
class WaveformHiddenTest : AccessibilityHarness() {
    @Test
    fun theWaveformIsDrawnNotSpoken() {
        // The meter is a picture of one fact the transport already carries. Left in the tree it was a
        // focus stop between the play button and the timer that announced the same state twice.
        assertTrue(
            "the waveform took a focus stop of its own",
            compose.onAllNodesWithContentDescription("Soundscape paused", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}

@RunWith(AndroidJUnit4::class)
class LayerMuteStateTest : AccessibilityHarness() {
    @Test
    fun aLayerSaysWhetherItIsMutedAndSoloed() {
        val name = layerName("moderate-rain")
        scrollTo(hasContentDescription("Mute $name"))
        assertEquals("an unmuted layer does not say so", "Not muted", stateOf("Mute $name"))
        press("Mute $name")
        await { runtime.saved.value.current.layers.any { it.name == name && it.mute } }
        compose.waitUntil(WAIT_MS) { stateOf("Mute $name") == "Muted" }

        assertEquals("an unsoloed layer does not say so", "Solo off", stateOf("Solo $name"))
        press("Solo $name")
        await { runtime.saved.value.current.layers.any { it.name == name && it.solo } }
        compose.waitUntil(WAIT_MS) { stateOf("Solo $name") == "Solo on" }
    }
}

@RunWith(AndroidJUnit4::class)
class LayerSliderTest : AccessibilityHarness() {
    @Test
    fun aSliderIsNamedWithItsLayerAndMovesWithoutADrag() {
        val name = layerName("moderate-rain")
        val volume = hasContentDescription("$name volume")
        scrollTo(volume)
        val node = compose.onNode(volume).fetchSemanticsNode()
        assertTrue(
            "a screen reader cannot set this slider's value",
            node.config.contains(SemanticsActions.SetProgress),
        )
        val said = stateOf("$name volume")
        assertTrue("the slider does not announce its level, it said $said", said!!.endsWith("%"))

        compose.onNode(volume).performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        await { runtime.saved.value.current.layers.any { it.name == name && it.gain < 0.3f } }
    }
}

@RunWith(AndroidJUnit4::class)
class LayerReorderTest : AccessibilityHarness() {
    @Test
    fun reorderingIsAnActionNotOnlyADrag() {
        val name = layerName("moderate-rain")
        val handle = hasContentDescription("Reorder $name")
        scrollTo(handle)
        val node = compose.onNode(handle).fetchSemanticsNode()
        assertEquals(listOf("Move up", "Move down"), customActions(node))
        assertFalse(
            "the drag handle is also a button, so a tap does nothing",
            node.config.contains(SemanticsActions.OnClick),
        )
        val before = runtime.saved.value.current.layers.map { it.id }
        // A custom action is a lambda in the node's configuration, so performing it is calling it,
        // which is what the platform does when it is chosen.
        compose.runOnIdle {
            node.config.getOrNull(SemanticsActions.CustomActions)!!
                .first { it.label == "Move down" }
                .action()
        }
        await { runtime.saved.value.current.layers.map { it.id } != before }
    }
}

@RunWith(AndroidJUnit4::class)
class HomeTouchTargetTest : AccessibilityHarness() {
    @Test
    fun everyControlOnHomeIsA48dpTarget() {
        val short = undersized()
        assertTrue("controls below the 48dp minimum on Home:\n" + short.joinToString("\n"), short.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsTouchTargetTest : AccessibilityHarness() {
    @Test
    fun everyControlOnSettingsIsA48dpTarget() {
        open("Settings")
        compose.waitUntil(WAIT_MS) { controls().isNotEmpty() }
        val short = undersized()
        assertTrue(
            "controls below the 48dp minimum in Settings:\n" + short.joinToString("\n"),
            short.isEmpty(),
        )
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryTouchTargetTest : AccessibilityHarness() {
    @Test
    fun everyControlInTheLibraryIsA48dpTarget() {
        open("Library")
        compose.waitUntil(WAIT_MS) { controls().isNotEmpty() }
        val short = undersized()
        assertTrue(
            "controls below the 48dp minimum in the Library:\n" + short.joinToString("\n"),
            short.isEmpty(),
        )
    }
}

@RunWith(AndroidJUnit4::class)
class PresetsTouchTargetTest : AccessibilityHarness() {
    @Test
    fun everyControlOnPresetsIsA48dpTarget() {
        open("Presets")
        compose.waitUntil(WAIT_MS) { controls().isNotEmpty() }
        val short = undersized()
        assertTrue(
            "controls below the 48dp minimum in Presets:\n" + short.joinToString("\n"),
            short.isEmpty(),
        )
    }
}

@RunWith(AndroidJUnit4::class)
class UnnamedControlTest : AccessibilityHarness() {
    @Test
    fun nothingOnHomeOrSettingsIsAnUnlabeledButton() {
        val home = unnamed()
        assertTrue("unlabeled controls on Home:\n" + home.joinToString("\n"), home.isEmpty())
        open("Settings")
        val settings = unnamed()
        assertTrue("unlabeled controls in Settings:\n" + settings.joinToString("\n"), settings.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class PresetCardTest : AccessibilityHarness() {
    @Test
    fun aPresetSaysWhetherItIsFavoriteAndWhatPlayingItMeans() {
        open("Presets")
        val name = Presets.all.first().name
        scrollTo(hasContentDescription("Play $name"))
        assertEquals("an unfavorited preset does not say so", "Not favorited", stateOf("Favorite $name"))
        assertEquals(
            "the play action does not name its preset",
            "Play $name",
            spoken(nodeOf("Play $name")),
        )
        assertTrue(
            "the favourite toggle's touch target is ${hit(nodeOf("Favorite $name")).first}dp wide",
            hit(nodeOf("Favorite $name")).first >= 48f,
        )
    }
}

@RunWith(AndroidJUnit4::class)
class TimerAnnouncementTest : AccessibilityHarness() {
    @Test
    fun settingATimerIsSaidOnceAndTheCountdownIsNot() {
        assertTrue("Home announces a timer it does not have", liveRegions().isEmpty())
        compose.runOnIdle { runtime.setTimer(30) }
        await { runtime.status.value.timerActive }
        compose.waitUntil(WAIT_MS) { liveRegions().isNotEmpty() }
        assertEquals(listOf("Sleep timer set, 30 minutes"), liveRegions())
        // The countdown ticks twice a second and crosses a minute boundary inside this window. A live
        // region keyed on it would read the clock aloud, once a minute, all night.
        val spokenOnce = liveRegions()
        advanceClock(65_000)
        compose.waitForIdle()
        assertEquals("the countdown is being announced", spokenOnce, liveRegions())
    }
}

@RunWith(AndroidJUnit4::class)
class PhraseAnnouncementTest : AccessibilityHarness() {
    @Test
    fun aPhraseSaysHowMuchOfItItUnderstood() {
        val field = hasContentDescription("Describe the mix you want")
        scrollTo(field)
        compose.onNode(field).performTextClearance()
        compose.onNode(field).performTextInput("heavy rain and a fan")
        compose.onNode(hasText("Make mix") and hasClickAction()).performClick()
        compose.waitUntil(WAIT_MS) { liveRegions().isNotEmpty() }
        assertEquals(listOf("Understood: 2 sounds ready to play"), liveRegions())
        // The chip's label is a layer and a level; pressing it removes the layer.
        assertEquals(
            "the chip does not say what pressing it does",
            "Remove Heavy rain from the mix, 71 percent",
            spoken(nodeOf("Remove Heavy rain from the mix, 71 percent")),
        )
    }
}

/**
 * The phrase box's own two fixed heights: at a doubled font a 46dp and a 44dp box both clip, and
 * both were under the minimum a finger needs anyway.
 */
@RunWith(AndroidJUnit4::class)
class PhraseBoxTargetTest : AccessibilityHarness() {
    @Test
    fun everyControlInThePhraseBoxIsAFingerTall() {
        val field = hasContentDescription("Describe the mix you want")
        scrollTo(field)
        compose.onNode(field).performTextClearance()
        compose.onNode(field).performTextInput("heavy rain and a fan")
        compose.onNode(hasText("Make mix") and hasClickAction()).performClick()
        compose.waitForIdle()
        val play = nodeWithText("Play this mix")
        assertTrue("Play this mix is ${dp(play).second}dp tall", dp(play).second >= 48f)
    }
}
