// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.NightfallApp
import dev.nightfall.NightfallTheme
import dev.nightfall.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The five-tap reveal has a spoken equivalent.
 *
 * The diagnostics surface is deliberately hidden: five taps on the Settings version row inside three
 * seconds. That gesture has no keyboard or screen-reader equivalent at all, so a person using TalkBack
 * could not reach a surface the app already ships. The action is the gesture said out loud, and it is
 * offered only where the surface exists: the F-Droid build answers `VersionTap` with the modifier it
 * was given, because it has nothing to reveal.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class DiagnosticsRevealActionTest {
    @get:Rule val compose = createComposeRule()

    private val app: NightfallApp = ApplicationProvider.getApplicationContext()

    @Test
    fun theVersionRowOffersTheRevealAsAnActionAndTheActionReveals() {
        val support = DiagnosticsSupport.of(app)
        compose.setContent {
            NightfallTheme(light = false) {
                SettingsScreen(app.runtime, onExport = {}, onCredits = {})
            }
        }
        // Settings is a lazy list and the version row is its last item: below the fold it is not
        // composed at all, and a node that was never composed has no actions to offer.
        compose
            .onAllNodes(hasScrollAction())
            .onFirst()
            .performScrollToNode(hasText("Nightfall", substring = true))
        compose.waitForIdle()

        val row =
            compose
                .onAllNodes(
                    SemanticsMatcher("has a custom action") {
                        !it.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty()
                    },
                    useUnmergedTree = true,
                )
                .onFirst()
                .fetchSemanticsNode()
        assertEquals(
            listOf("Show diagnostics"),
            row.config.getOrNull(SemanticsActions.CustomActions)!!.map { it.label },
        )
        // Nothing hints at the surface: it still reads as the app's name and version.
        assertTrue(
            "the row is no longer ordinary copy",
            row.config.getOrNull(SemanticsProperties.Text)!!.joinToString(" ") { it.text }
                .startsWith("Nightfall"),
        )

        compose.runOnIdle { row.config.getOrNull(SemanticsActions.CustomActions)!!.single().action() }
        compose.waitForIdle()
        assertTrue("the action did not reveal the surface", support.revealed.value)
    }
}
