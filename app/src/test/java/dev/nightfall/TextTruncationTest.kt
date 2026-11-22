// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The failure mode [BigFontHarness] cannot see: text that is the right size, inside the window, and
 * still unreadable because its last words are gone behind an ellipsis.
 *
 * "System audio output" was drawn as "System audio outp…" in Home's now-playing row at 2.0×. Every
 * number in [FontScaleTest] was green when it was: the row was the width it should be and the text
 * was the height it should be, and only the words were missing.
 *
 * So this harness measures the words. `Text` publishes the [TextLayoutResult] it drew through a
 * semantics action — the same one accessibility services read — and an ellipsis is a fact about a
 * line of that layout, not something you have to look at a picture to find. Real glyph advances are
 * the instrument's other half, which is why the sandbox runs in native graphics mode and the scale
 * comes from `RuntimeEnvironment.setFontScale`: the same configuration change the system sends, so
 * Compose measures the app's copy with the fonts Android would give it.
 *
 * One scenario per class, because this repository runs one JVM per test class.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
abstract class TruncationHarness : PlaybackHarness() {

    /** The scale a person turns on in Android's accessibility settings, applied the way Android does. */
    protected fun atScale(scale: Float) {
        compose.runOnIdle { RuntimeEnvironment.setFontScale(scale) }
        compose.waitForIdle()
    }

    /**
     * Every string Compose's own layout had to cut, with the room it was given.
     *
     * An ellipsized line is the defect this exists for. A layout shorter than its own paragraph is
     * the other half of the same thing: the box closed over lines it had already laid out.
     */
    protected fun cutText(): List<String> {
        val findings = LinkedHashSet<String>()
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.firstOrNull() ?: return@forEach
            val cut = (0 until layout.lineCount).firstOrNull { layout.isLineEllipsized(it) }
            val drawn = layout.size.height / compose.density.density
            val laidOut = layout.multiParagraph.height / compose.density.density
            if (cut == null && layout.size.height >= layout.multiParagraph.height) return@forEach
            findings +=
                "\"%s\": %s"
                    .format(
                        layout.layoutInput.text.text,
                        if (cut != null)
                            "line ${cut + 1} of ${layout.lineCount} ellipsized in " +
                                "%.1fdp of room".format(layout.size.width / compose.density.density)
                        else "clipped: %.1fdp drawn of %.1fdp laid out".format(drawn, laidOut),
                    )
        }
        return findings.toList()
    }

    /** The screen's own list: the tallest scrollable, so a chip rail above it is not what scrolls. */
    private fun screenList() =
        compose.onAllNodes(hasScrollAction()).let { all ->
            val nodes = all.fetchSemanticsNodes()
            all[nodes.indices.maxBy { nodes[it].boundsInRoot.height }]
        }

    /**
     * Walks a screen from its first screenful to its last, collecting what was cut along the way.
     *
     * A lazy list composes only what is on screen, so one frame of this check sees one frame of the
     * app's copy. Twelve steps of a third of the window is more than any of these lists holds.
     */
    protected fun cutTextAcrossTheScreen(): List<String> {
        val findings = LinkedHashSet<String>()
        findings += cutText()
        repeat(SWEEPS) {
            screenList().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SWIPE_PX) }
            compose.waitForIdle()
            findings += cutText()
        }
        return findings.toList()
    }

    /** Opens a tab the way a person does. */
    protected fun open(tab: String) {
        compose.onNodeWithText(tab, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    protected companion object {
        const val SWEEPS = 12
        const val SWIPE_PX = 300f
    }
}

@RunWith(AndroidJUnit4::class)
class HomeTruncationTest : TruncationHarness() {
    @Test
    fun noStringOnHomeIsEllipsizedAtTwoHundredPercent() {
        atScale(2f)
        val cut = cutTextAcrossTheScreen()
        assertTrue("text cut at 200% on Home:\n" + cut.joinToString("\n"), cut.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryTruncationTest : TruncationHarness() {
    @Test
    fun noStringInTheLibraryIsEllipsizedAtTwoHundredPercent() {
        atScale(2f)
        open("Library")
        val cut = cutTextAcrossTheScreen()
        assertTrue("text cut at 200% in the Library:\n" + cut.joinToString("\n"), cut.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class PresetsTruncationTest : TruncationHarness() {
    @Test
    fun noStringInPresetsIsEllipsizedAtTwoHundredPercent() {
        atScale(2f)
        open("Presets")
        val cut = cutTextAcrossTheScreen()
        assertTrue("text cut at 200% in Presets:\n" + cut.joinToString("\n"), cut.isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsTruncationTest : TruncationHarness() {
    @Test
    fun noStringInSettingsIsEllipsizedAtTwoHundredPercent() {
        atScale(2f)
        open("Settings")
        val cut = cutTextAcrossTheScreen()
        assertTrue("text cut at 200% in Settings:\n" + cut.joinToString("\n"), cut.isEmpty())
    }
}
