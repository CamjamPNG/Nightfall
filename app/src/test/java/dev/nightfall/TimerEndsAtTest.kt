// SPDX-License-Identifier: MIT
package dev.nightfall

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nightfall.core.EndsAt
import dev.nightfall.core.SavedTimer
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The timer's second shape, end to end: what Home says, what the sheet's own controls are to a
 * screen reader and a finger, and what a restart does to a countdown chosen by the clock.
 *
 * The arithmetic behind a chosen time is pinned in `core`'s `EndsAtTest`. These classes prove the
 * screen hands a time to it, says what it did, and comes back to the same moment.
 *
 * Two things are worth knowing before reading further. This repository's suite has never been able
 * to drive an `AlertDialog` under Robolectric — opening the pre-existing sleep sheet exhausts the
 * shadow text-layout registry just as surely as opening this one — so the sheet's content is hosted
 * and measured on its own rather than through the dialog window around it. And the words on Home are
 * driven through the runtime, which is the same call the sheet's buttons make.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class TimerEndsAtTest : AccessibilityHarness() {

    @Test
    fun theButtonAndTheAnnouncementSayTheTimeNotTheCountdown() {
        val sixThirty = clockLabel(app, EndsAt.SUGGESTIONS.first())
        assertTrue("Home announces a timer it does not have", liveRegions().isEmpty())

        compose.runOnIdle { runtime.setTimerUntil(EndsAt.SUGGESTIONS.first()) }
        await { runtime.status.value.timerActive }

        assertEquals("the chosen time did not survive as a time", 390, runtime.timerEndsAtMinute)
        val remaining = runtime.timer.remainingMs(SystemClock.elapsedRealtime())
        assertTrue(
            "an ends-at timer is a $remaining ms countdown, outside the day it allows",
            remaining in 60_000..(1440 * 60_000L),
        )
        val sleepTimer = app.getString(R.string.home_a11y_sleep_timer)
        compose.waitUntil(WAIT_MS) { stateOf(sleepTimer) == "Ends $sixThirty" }
        compose.waitUntil(WAIT_MS) { liveRegions().isNotEmpty() }
        // Said as the time it was chosen at, not as the countdown it became.
        assertEquals(listOf("Timer ends at $sixThirty"), liveRegions())

        // The countdown ticks twice a second and crosses a minute inside this window. A live region
        // keyed on the clock it is approaching would read the time aloud, once a minute, all night.
        val spokenOnce = liveRegions()
        advanceClock(65_000)
        compose.waitForIdle()
        assertEquals("the countdown is being announced", spokenOnce, liveRegions())
        assertEquals(
            "the button stopped saying the time it was set to",
            "Ends $sixThirty",
            stateOf(sleepTimer),
        )
    }
}

/**
 * The sheet's new controls, hosted on their own: the three one-tap times and the picker.
 *
 * They live in a dialog, which this suite cannot drive (see the note above), so what is measured
 * here is the content the dialog shows — the controls a person lands on first, their names, their
 * sizes, and what pressing them does to the runtime.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class TimerEndsAtControlsTest {

    @get:Rule val compose = createComposeRule()

    private val app: NightfallApp
        get() = ApplicationProvider.getApplicationContext()

    private fun controls(): List<SemanticsNode> =
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    private fun spoken(node: SemanticsNode): String? {
        val description =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        if (!description.isNullOrBlank()) return description
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        return text?.takeIf { it.isNotBlank() }
    }

    private fun size(node: SemanticsNode): Pair<Float, Float> {
        val scale = compose.density.density
        return (node.touchBoundsInRoot.width / scale).toFloat() to
            (node.touchBoundsInRoot.height / scale).toFloat()
    }

    private fun onScreen(text: String) =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theTimesAreNamedForWhatTheyDoAndThePickerSaysWhatItWillSet() {
        val draft = ClockDraft(is24Hour = false)
        compose.setContent {
            MaterialTheme {
                Column {
                    EndsAtSuggestions(app.runtime, onDismiss = {})
                    EndAtPicker(draft)
                }
            }
        }
        compose.waitForIdle()

        val sixThirty = clockLabel(app, EndsAt.SUGGESTIONS.first())
        EndsAt.SUGGESTIONS.forEach { minute ->
            val name = app.getString(R.string.dialog_timer_ends_at_action, clockLabel(app, minute))
            assertTrue(
                "no control named \"$name\"; the times are ${
                    controls().mapNotNull { spoken(it) }
                }",
                compose.onAllNodes(hasContentDescription(name)).fetchSemanticsNodes().isNotEmpty(),
            )
        }

        val short =
            controls().mapNotNull { node ->
                val (w, h) = size(node)
                if (w < 48f || h < 48f)
                    "%.1f x %.1f dp: %s".format(w, h, spoken(node) ?: "(no name)") else null
            }
        assertTrue("controls under 48dp in the timer's clock row and picker:\n" + short.joinToString("\n"), short.isEmpty())
        val mute = controls().filter { spoken(it) == null }
        assertTrue(
            "unlabeled controls in the timer's clock row and picker: " +
                mute.joinToString { "${size(it).first}x${size(it).second}dp" },
            mute.isEmpty(),
        )

        // What the two fields hold, and what they add up to: the words Home will carry.
        assertTrue("the picker does not preview the time", onScreen("Ends $sixThirty"))
        assertEquals("the draft is not the time on screen", 6 * 60 + 30, draft.minuteOfDay)

        // Pressing a one-tap time is the whole gesture: it sets the countdown and closes the sheet.
        compose
            .onAllNodes(
                hasContentDescription(
                    app.getString(R.string.dialog_timer_ends_at_action, sixThirty)
                )
            )
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("a one-tap time set a different time", 390, app.runtime.timerEndsAtMinute)

        // The half of the day is a choice with a state, not a label.
        compose.onNode(hasText(app.getString(R.string.dialog_timer_pm)) and hasClickAction())
            .performClick()
        compose.waitForIdle()
        assertTrue("choosing PM did not move the time", draft.afternoon)
        assertEquals("PM kept the morning's hour", 18 * 60 + 30, draft.minuteOfDay)
        assertTrue("the preview did not follow the choice", onScreen("Ends ${clockLabel(app, 18 * 60 + 30)}"))

        // An hour that is not a time is refused in the field, says so, and leaves the dialog's
        // confirm with nothing to set.
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("13")
        compose.waitForIdle()
        assertEquals("an impossible hour still made a time", null, draft.minuteOfDay)
        assertTrue("the picker does not say the time is invalid", onScreen(app.getString(R.string.dialog_timer_invalid)))
    }
}

@RunWith(AndroidJUnit4::class)
class TimerClockFormatTest : PlaybackHarness() {

    @Test
    fun theTimeIsWrittenTheWayThisDeviceWritesTimes() {
        val resolver = app.contentResolver
        val before = Settings.System.getString(resolver, Settings.System.TIME_12_24)
        try {
            Settings.System.putString(resolver, Settings.System.TIME_12_24, "12")
            val twelve = clockLabel(app, 390)
            assertTrue("a 12-hour device did not read half past six as such: $twelve", twelve.contains("6:30"))
            assertTrue("a 12-hour device did not say which half of the day: $twelve", twelve.contains("AM"))

            Settings.System.putString(resolver, Settings.System.TIME_12_24, "24")
            val twentyFour = clockLabel(app, 390)
            assertTrue("a 24-hour device did not read half past six as such: $twentyFour", twentyFour.contains("6:30"))
            assertFalse(
                "a 24-hour device still said AM or PM: $twentyFour",
                twentyFour.contains("AM") || twentyFour.contains("PM"),
            )
            // The same minute of the day is 18:30 on a 24-hour clock. A formatter that ignored the
            // setting would produce the morning's string for both.
            assertTrue(
                "the device's 12/24-hour setting is not being read: ${clockLabel(app, 18 * 60 + 30)}",
                clockLabel(app, 18 * 60 + 30).contains("18:30"),
            )
        } finally {
            Settings.System.putString(resolver, Settings.System.TIME_12_24, before)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class TimerRestoreTest : AccessibilityHarness() {

    @Test
    fun aTimerStoresItsOwnEndMomentAndComesBackToIt() {
        compose.runOnIdle { runtime.setTimerUntil(390) }
        await { runtime.status.value.timerActive }
        // A countdown on a silent screen waits; one behind playing audio runs, and that is the shape
        // a night interrupted by a process death is in.
        press(app.getString(R.string.home_action_play))
        deliverStartCommand()
        await { runtime.status.value.playing }
        await { !runtime.saved.value.timer.paused }

        // First half: the running countdown is written as the wall-clock moment it ends at, which is
        // the only shape a restart can land on. Robolectric does not intercept the wall clock in this
        // sandbox, so the ten minutes that pass during a real restart cannot be simulated by moving
        // it; what can be asserted is that the moment stored is the moment the countdown ends at.
        val liveEnd =
            System.currentTimeMillis() + runtime.timer.remainingMs(SystemClock.elapsedRealtime())
        val stored = runtime.saved.value.timer
        assertEquals("the stored timer forgot the time it was set to", 390, stored.targetMinute)
        assertFalse("a running timer was stored as paused", stored.paused)
        assertTrue(
            "the stored end ${stored.endMs} is not the countdown's own end $liveEnd",
            kotlin.math.abs(stored.endMs - liveEnd) <= 1_000,
        )
        awaitSaved()
        // The Preferences file is protobuf with the state's JSON in one string value, pretty-printed,
        // so the assertion is on the field and its value with the whitespace taken out.
        val written =
            String(
                    checkNotNull(
                            app.filesDir.walkTopDown()
                                .firstOrNull { it.name == "nightfall.preferences_pb" }
                        )
                        .readBytes(),
                    Charsets.ISO_8859_1,
                )
                .filterNot { it == ' ' }
        assertTrue(
            "the end moment never reached local storage",
            written.contains("\"endMs\":${stored.endMs}"),
        )

        // Second half: what a fresh process reads. The night was interrupted twenty minutes before
        // its end, so that is what is on disk when the next process starts.
        val endMs = System.currentTimeMillis() + 20 * 60_000
        compose.runOnIdle { runtime.change { it.copy(timer = SavedTimer(endMs = endMs, targetMinute = 390)) } }
        awaitSaved()

        val restored = NightfallRuntime(app)
        await { restored.ready.value }
        assertTrue("the timer did not come back", restored.timer.active)
        assertTrue("a restored timer does not wait for playback to resume", restored.timer.paused)
        assertEquals("the restored timer forgot which time it was set to", 390, restored.timerEndsAtMinute)
        val remaining = restored.timer.remainingMs(SystemClock.elapsedRealtime())
        assertTrue("the restored countdown is $remaining ms, not the twenty minutes left", remaining in 19 * 60_000..20 * 60_000)
        val restoredEnd = System.currentTimeMillis() + remaining
        assertTrue(
            "the restored timer ends at a different moment: $endMs then $restoredEnd",
            kotlin.math.abs(restoredEnd - endMs) <= 2_000,
        )
        restored.scope.cancel()
    }
}

@RunWith(AndroidJUnit4::class)
class TimerPastRestoreTest : PlaybackHarness() {

    @Test
    fun aTimerWhoseMomentHasGoneIsNotRestarted() {
        // A night set to end at 6:30 whose process died: the moment has gone by the time anything
        // reads it back. Restarting the countdown would play a night nobody asked for.
        val past = System.currentTimeMillis() - 60_000
        compose.runOnIdle { runtime.change { it.copy(timer = SavedTimer(endMs = past, targetMinute = 390)) } }
        awaitSaved()

        val restored = NightfallRuntime(app)
        await { restored.ready.value }
        assertFalse("a night whose end had passed was restarted", restored.timer.active)
        assertEquals("a stale end time is still being shown", -1, restored.status.value.timerEndsAtMinute)
        assertEquals("the state still holds a timer that is over", SavedTimer(), restored.saved.value.timer)
        restored.scope.cancel()
    }
}
