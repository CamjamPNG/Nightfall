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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nightfall.update.UpdateBanner

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
            NightfallTheme(saved.settings.light) {
                Box(Modifier.fillMaxSize()) {
                    NightfallScreen(vm)
                    UpdateBanner(vm.updates, Modifier.align(Alignment.TopCenter).statusBarsPadding())
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33)
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onStart() {
        super.onStart()
        // Throttled inside the updater: at most one manifest read per six hours, and only online.
        (application as NightfallApp).updates.onForeground()
    }
}
