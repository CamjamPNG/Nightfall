// SPDX-License-Identifier: MIT
package dev.nightfall

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nightfall.diagnostics.DiagnosticsSurface
import dev.nightfall.update.UpdateForegroundEffect
import dev.nightfall.update.UpdateOverlay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val vm: NightfallViewModel = viewModel()
            val saved by vm.saved.collectAsStateWithLifecycle()
            // One boolean, not the status itself: play and pause are the only changes the updater
            // needs, and collecting the whole status here would invalidate the screen beside it.
            val playing = remember(vm) { vm.status.map { it.playing }.distinctUntilChanged() }
            NightfallTheme(saved.settings.light) {
                DiagnosticsSurface.Host(vm.runtime)
                Box(Modifier.fillMaxSize()) {
                    NightfallScreen(vm)
                    UpdateOverlay(Modifier.align(Alignment.TopCenter).statusBarsPadding())
                    UpdateForegroundEffect(playing)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33)
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
