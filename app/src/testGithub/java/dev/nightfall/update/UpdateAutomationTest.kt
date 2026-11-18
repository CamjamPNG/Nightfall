// SPDX-License-Identifier: MIT
package dev.nightfall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic channel's rules. The first test here is the one that matters most: an install the
 * app starts by itself must never land in playing audio, because it kills the process.
 */
class UpdateAutomationTest {
    private val now = 1_700_000_000_000L
    private val minute = 60 * 1000L
    private val hour = 60 * minute

    @Test
    fun anUnattendedInstallNeverInterruptsPlayback() {
        for (automatic in listOf(true, false)) {
            for (playback in listOf(Playback.PLAYING, Playback.UNKNOWN)) {
                assertFalse(
                    "automatic=$automatic playback=$playback must never install on its own",
                    UpdateAutomation.mayInstallNow(automatic, playback),
                )
            }
        }
        assertTrue(UpdateAutomation.mayInstallNow(automatic = true, playback = Playback.STOPPED))
        assertFalse(
            "with the setting off, the tap decides and the app installs nothing itself",
            UpdateAutomation.mayInstallNow(automatic = false, playback = Playback.STOPPED),
        )
    }

    @Test
    fun aSessionThatPlaysAllNightIsNeverUpdatedUnderneath() {
        // The night this guard exists for: audio starts before the download finishes, stops only in
        // the morning. Nothing but the final transition may open the install window.
        val playback =
            listOf(
                Playback.PLAYING,
                Playback.PLAYING,
                Playback.PLAYING,
                Playback.PLAYING,
                Playback.STOPPED,
            )
        val windows = playback.map { UpdateAutomation.mayInstallNow(automatic = true, playback = it) }
        assertEquals(listOf(false, false, false, false, true), windows)
        assertEquals("the only install is the one after playback stopped", 1, windows.count { it })
    }

    @Test
    fun anInstallTheAppCannotPlaceWaitsRatherThanGuesses() {
        // UNKNOWN is what a foreground entry sees before the app has reported playback: treating it
        // as stopped would install into audio the moment the updater loaded a little faster than
        // the status flow.
        assertFalse(UpdateAutomation.mayInstallNow(automatic = true, playback = Playback.UNKNOWN))
    }

    @Test
    fun theAppsOwnCheckHonoursTheSettingTheConnectionAndBothIntervals() {
        val fresh = 0L
        assertTrue(UpdateAutomation.wantsAutomaticCheck(true, now, fresh, fresh, online = true))
        assertFalse("the setting is the owner's off switch", UpdateAutomation.wantsAutomaticCheck(false, now, fresh, fresh, online = true))
        assertFalse("no connection, no request", UpdateAutomation.wantsAutomaticCheck(true, now, fresh, fresh, online = false))
        assertFalse(
            "inside the fifteen-minute floor",
            UpdateAutomation.wantsAutomaticCheck(true, now, now - 5 * minute, fresh, online = true),
        )
        assertFalse(
            "inside the six-hour interval since the last answer",
            UpdateAutomation.wantsAutomaticCheck(true, now, now - hour, now - 2 * hour, online = true),
        )
        assertTrue(
            "past both",
            UpdateAutomation.wantsAutomaticCheck(true, now, now - 7 * hour, now - 7 * hour, online = true),
        )
        assertTrue(
            "a failed attempt spends the floor only, never the six-hour window",
            UpdateAutomation.wantsAutomaticCheck(true, now, now - 20 * minute, now - 7 * hour, online = true),
        )
        assertTrue(
            "a corrected clock is expired, not a lock",
            UpdateAutomation.wantsAutomaticCheck(true, now, now + 2 * hour, now + 2 * hour, online = true),
        )
    }

    @Test
    fun theChannelUpdatesItselfByDefault() {
        assertTrue("the github channel arrives with automatic updates on", UpdateAutomation.AUTOMATIC_BY_DEFAULT)
    }

    @Test
    fun aDownloadFromAnEarlierSessionSurvivesOnlyWhileItIsStillNewer() {
        assertTrue(UpdateAutomation.resumesAfterRestart(pendingVersionCode = 12, installedVersionCode = 11, filePresent = true))
        assertFalse(
            "the install already happened, so the record is spent",
            UpdateAutomation.resumesAfterRestart(pendingVersionCode = 11, installedVersionCode = 11, filePresent = true),
        )
        assertFalse(
            "an older build is never a pending install",
            UpdateAutomation.resumesAfterRestart(pendingVersionCode = 9, installedVersionCode = 11, filePresent = true),
        )
        assertFalse(
            "Android may clear the cache; a missing file is not an install",
            UpdateAutomation.resumesAfterRestart(pendingVersionCode = 12, installedVersionCode = 11, filePresent = false),
        )
    }

    @Test
    fun aWaitingUpdateIsOnScreenAndCanBeDismissedLikeAnyOther() {
        val waiting = UpdateState.ReadyToInstall("1.0.3", 6)
        assertTrue("the owner is told which window it is waiting for", shouldShowBanner(waiting, null))
        assertFalse("dismissing hides the row, never the update", shouldShowBanner(waiting, "1.0.3"))
        assertTrue(shouldShowBanner(waiting, "1.0.2"))
        assertFalse("waiting is not work in flight", waiting.isBusy())
        assertTrue(UpdateState.Installing("1.0.3").isBusy())
        assertTrue(UpdateState.Downloading("1.0.3", 0.4f).isBusy())
        assertTrue(UpdateState.Checking.isBusy())
    }
}
