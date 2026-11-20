// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.NightPlan
import dev.nightfall.core.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Playing a themed night from the app's own controls keeps its plan.
 *
 * This is the defect a model test cannot see: a preset is applied by renaming its layers so the whole
 * mix crossfades in, and a phase plan written in the old ids then names nothing, so every layer plays
 * its own gain all night and the theme quietly becomes a plain mix. The path below is the one a person
 * takes — save the theme into Your mixes, then tap its chip on Home — and the assertion is the plan's
 * own answer at two different hours, not the presence of a field.
 */
@RunWith(AndroidJUnit4::class)
class ThemedPresetPlayTest : PlaybackHarness() {

    @Test
    fun aThemedNightPlayedFromHomeKeepsItsPlan() {
        val theme = Presets.all.first { it.id == "storm-that-passes" }
        compose.runOnIdle { runtime.savePreset(theme) }
        await { runtime.saved.value.presets.any { it.name == "Storm That Passes" } }

        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        // The strip of mixes sits below the fold of the home list, so it has to be scrolled to the way
        // a person scrolls it. Two scrollables are on the screen — the list and the strip inside it —
        // and only the outer one scrolls to the chip.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Storm That Passes"))
        compose.onNodeWithText("Storm That Passes", useUnmergedTree = true).performClick()
        await { runtime.saved.value.current.name == "Storm That Passes" }

        val mix = runtime.saved.value.current
        assertFalse("the played mix is not a themed night", mix.endless.phases.isEmpty())
        val ids = mix.layers.map { it.id }.toSet()
        val unknown = mix.endless.phases.flatMap { it.layers }.filter { it.layerId !in ids }
        assertTrue("phase targets name layers the applied mix does not have: $unknown", unknown.isEmpty())

        val plan = NightPlan.of(mix)!!
        val heavy = mix.layers.first { it.soundId == "heavy-rain" }.id
        val later = plan.amount(heavy, 0, 300 * 60.0)
        println("heavy rain at the start %.2f, at five hours %.2f".format(plan.amount(heavy, 0, 0.0), later))
        assertEquals("the theme's opening is not what plays", 1f, plan.amount(heavy, 0, 0.0), 1e-4f)
        assertEquals("the plan stopped applying when the preset was played", 0f, later, 1e-4f)
    }
}
