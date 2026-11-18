// SPDX-License-Identifier: MIT
package dev.nightfall.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The F-Droid channel has no updater: no manifest URL, no download code, no network permission, no
 * socket. This build only reports its installed version, and Settings says where updates come from.
 */
private class NoUpdater(context: Context) : UpdateApi {
    override val supported = false
    override val installedVersion: String =
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code =
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
                else @Suppress("DEPRECATION") info.versionCode
            "${info.versionName ?: "unknown"} ($code)"
        } catch (e: Exception) {
            "unknown"
        }
    private val idle = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = idle.asStateFlow()

    override fun check(manual: Boolean) = Unit

    override fun install() = Unit

    override fun onForeground() = Unit

    override fun dismiss() = Unit

    override fun openInstallPermission() = Unit
}

object UpdateSupport {
    private var instance: UpdateApi? = null

    fun of(context: Context): UpdateApi =
        instance ?: synchronized(this) { instance ?: NoUpdater(context.applicationContext).also { instance = it } }
}
