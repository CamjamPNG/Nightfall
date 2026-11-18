// SPDX-License-Identifier: MIT
package dev.nightfall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The commit-time half of the playback guard. The copy of a ninety-megabyte APK takes seconds with
 * Play still live, so the window is read again right before Android commits; this pins that the
 * second read is the one that decides, and that a resumed playback never reaches the commit.
 */
class InstallWindowTest {
    /** A playback fact the test can move, exactly as the activity's status flow moves it. */
    private class Audio {
        var playing = false
        fun fact() = if (playing) Playback.PLAYING else Playback.STOPPED
    }

    private val audio = Audio()
    private val events = mutableListOf<String>()
    private var committed = false
    private var abandoned = false

    private fun install(guarded: Boolean, duringCopy: () -> Unit): InstallWindow.Outcome =
        InstallWindow.copyThenCommit(
            copy = {
                events += "copy"
                duringCopy()
            },
            windowOpen = {
                events += "window"
                UpdateAutomation.mayCommitNow(guarded, automatic = true, playback = audio.fact())
            },
            commit = {
                events += "commit"
                committed = true
            },
            abandon = {
                events += "abandon"
                abandoned = true
            },
        )

    @Test
    fun playbackThatStartsDuringTheCopyNeverReachesTheCommit() {
        val outcome = install(guarded = true) { audio.playing = true }
        assertEquals(InstallWindow.Outcome.DEFERRED, outcome)
        assertFalse("an automatic install must never commit into playing audio", committed)
        assertTrue("the session is abandoned, not left dangling", abandoned)
        assertEquals("the window is read after the copy, before the commit", listOf("copy", "window", "abandon"), events)
    }

    @Test
    fun aCopyThatFinishesWhileNothingIsPlayingCommits() {
        val outcome = install(guarded = true) { }
        assertEquals(InstallWindow.Outcome.COMMITTED, outcome)
        assertTrue(committed)
        assertFalse(abandoned)
        assertEquals(listOf("copy", "window", "commit"), events)
    }

    @Test
    fun playbackThatStartsAndStopsAgainDuringTheCopyStillCommits() {
        // The re-read is of the fact at commit time, not of whether audio ever played.
        val outcome = install(guarded = true) {
            audio.playing = true
            audio.playing = false
        }
        assertEquals(InstallWindow.Outcome.COMMITTED, outcome)
    }

    @Test
    fun aTapIsNotHeldBackByTheCommitCheck() {
        // The owner asked for this one, so the copy-time fact is not a veto.
        val outcome = install(guarded = false) { audio.playing = true }
        assertEquals(InstallWindow.Outcome.COMMITTED, outcome)
        assertTrue(committed)
    }

    @Test
    fun anUnknownPlaybackFactDefersRatherThanGuesses() {
        val outcome =
            InstallWindow.copyThenCommit(
                copy = { },
                windowOpen = { UpdateAutomation.mayCommitNow(guarded = true, automatic = true, playback = Playback.UNKNOWN) },
                commit = { committed = true },
                abandon = { abandoned = true },
            )
        assertEquals(InstallWindow.Outcome.DEFERRED, outcome)
        assertFalse(committed)
        assertTrue(abandoned)
    }

    @Test
    fun theSettingStillGovernsTheCommitTimeRead() {
        assertFalse(UpdateAutomation.mayCommitNow(guarded = true, automatic = false, playback = Playback.STOPPED))
        assertTrue(UpdateAutomation.mayCommitNow(guarded = false, automatic = false, playback = Playback.PLAYING))
    }
}
