// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.nightfall.NightfallRuntime

/**
 * The F-Droid channel's half of the hidden diagnostics surface: nothing.
 *
 * This build carries no journal, no switch, no gesture and no report, and it never mentions any of
 * them. `tools/check_release.py` proves that from the built F-Droid APK's dex tables rather than from
 * this file, so a class added to `app/src/github`'s diagnostics package is covered the day it is
 * written.
 */
object DiagnosticsSurface {

    @Composable
    fun VersionTap(base: Modifier): Modifier = base

    @Composable
    fun Section() = Unit

    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun Host(runtime: NightfallRuntime) = Unit
}
