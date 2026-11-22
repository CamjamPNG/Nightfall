// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.io.File

/**
 * The state every surface shows, as the service last published it.
 *
 * The widget and the Quick Settings tile both paint from this one record, so a control can never
 * show an idea of playback that came from its own last tap. Nothing here polls: [publish] is called
 * by `PlaybackService` with each status the runtime actually reports, and it repaints whatever is
 * on screen right now.
 *
 * The record lives in private preferences with the kernel's boot identifier and the
 * `elapsedRealtime` mark it was written at. A stored state carrying another boot's identifier is
 * replaced with stopped rather than skipped past — uptime alone only catches a reboot the launcher
 * asks about early in the new boot, and a short previous boot leaves a mark this boot passes in
 * minutes. A newly added tile or widget therefore reads as stopped rather than as whatever was
 * running when the phone went down.
 */
object MixSurface {
    // The name predates the tile: changing it would drop the state an updated install already holds.
    private const val PREFS = "nightfall_widget"
    private const val KEY_STATE = "state"
    private const val KEY_STATE_AT = "state_at"
    private const val KEY_BOOT = "boot"

    /**
     * `elapsedRealtime` counts from boot. A stored mark that is ahead of now belongs to an earlier
     * boot than this one, and audio cannot survive a reboot. The mark alone only catches a reboot
     * seen early in the new boot — the boot identifier is what settles it at any uptime.
     */
    private const val REBOOT_SLACK_MS = 5_000L

    /** Kernel boot identifier, read once: it cannot change while this process lives. */
    @Volatile private var boot: String? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What was last written, before the reboot guard has a say. */
    private fun stored(context: Context): MixState? {
        val name = prefs(context).getString(KEY_STATE, null) ?: return null
        return MixState.entries.firstOrNull { it.name == name }
    }

    fun state(context: Context): MixState {
        val state = stored(context) ?: return MixState.STOPPED
        val written = prefs(context).getString(KEY_BOOT, null)
        val at = prefs(context).getLong(KEY_STATE_AT, 0L)
        if (
            state != MixState.STOPPED &&
                (written != bootId() || SystemClock.elapsedRealtime() + REBOOT_SLACK_MS < at)
        ) {
            record(context, MixState.STOPPED)
            return MixState.STOPPED
        }
        return state
    }

    /** `/proc`'s boot identifier, with the boot's wall-clock instant as a fallback. */
    private fun bootId(): String =
        boot
            ?: (
                    runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                    // A clock corrected mid-boot moves this value, so it identifies a boot only
                    // to the nearest hour; the fallback is a coarse guard, not an exact one.
                    ?: "wall:" +
                        (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 3_600_000L
                )
                .also { boot = it }

    private fun record(context: Context, state: MixState) {
        prefs(context)
            .edit()
            .putString(KEY_STATE, state.name)
            .putLong(KEY_STATE_AT, SystemClock.elapsedRealtime())
            .putString(KEY_BOOT, bootId())
            .apply()
    }

    /**
     * Records the state and repaints every surface, if it is news. Called on the main thread with
     * each status the runtime publishes, so the comparison is what keeps a quarter-second meter tick
     * from touching the launcher or the shade.
     */
    fun publish(context: Context, state: MixState) {
        if (stored(context) == state) return
        record(context, state)
        PlaybackWidget.refresh(context)
        NightfallTileService.refresh()
    }
}
