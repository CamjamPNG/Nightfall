// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Consent begins at the switch.
 *
 * Android keeps the exits of this app's own process from before the switch was ever turned on, and it
 * is the only place the journal could read backwards past consent: an app that crashed in March would
 * be described in July by somebody who turned the switch on today. The cutoff is the record of "this
 * is when the person said yes", and it only ever moves forward, so a disabled interval cannot be
 * imported by turning collection back on afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ConsentTest {
    private fun prefs() =
        DiagnosticsPrefs(
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("diagnostics-test", Context.MODE_PRIVATE)
        )

    @Test
    fun theFirstConsentIsTheCutoff() {
        val prefs = prefs()
        assertEquals("a fresh install starts with no cutoff", 0L, prefs.lastExit)

        prefs.adoptConsent(1_000L)

        assertEquals(1_000L, prefs.lastExit)
    }

    @Test
    fun theCutoffOnlyMovesForward() {
        val prefs = prefs()
        prefs.adoptConsent(2_000L)

        prefs.adoptConsent(1_500L)

        assertEquals(
            "a later consent re-opened an interval collection was off for",
            2_000L,
            prefs.lastExit,
        )
    }

    @Test
    fun anImportedExitIsNotRolledBackByTheNextConsent() {
        val prefs = prefs()
        prefs.adoptConsent(1_000L)
        prefs.lastExit = 5_000L

        prefs.adoptConsent(3_000L)

        assertEquals("an already-imported exit would be imported again", 5_000L, prefs.lastExit)
    }

    @Test
    fun turningTheSwitchOnStartsTheCutoffAtThatMoment() {
        val diagnostics = Diagnostics(ApplicationProvider.getApplicationContext())
        val before = System.currentTimeMillis()

        diagnostics.setCollecting(true)

        val cutoff = DiagnosticsPrefs(
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        ).lastExit
        assertTrue("turning the switch on did not start the cutoff", cutoff >= before)
        assertTrue("the cutoff is not the moment of consent", cutoff <= System.currentTimeMillis())

        diagnostics.setCollecting(false)
    }

    @Test
    fun theSwitchIsOffUntilItIsTurnedOn() {
        val diagnostics = Diagnostics(ApplicationProvider.getApplicationContext())

        assertFalse("collection was on before anybody turned it on", diagnostics.collecting.value)

        diagnostics.setCollecting(true)
        assertTrue(diagnostics.collecting.value)

        diagnostics.setCollecting(false)
        assertFalse(diagnostics.collecting.value)
    }
}
