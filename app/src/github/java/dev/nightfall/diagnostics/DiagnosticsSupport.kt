// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.os.SystemClock
import dev.nightfall.NightfallRuntime
import dev.nightfall.update.UpdateState
import dev.nightfall.update.UpdateSupport
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The two switches this feature has, and the one bookmark it needs.
 *
 * [revealed] is the hidden surface's visibility, not a channel: the app does not know whether it was
 * ever tapped until it was. Uninstalling clears it with everything else in the app's private storage,
 * so a fresh install always starts hidden.
 */
class DiagnosticsPrefs(private val prefs: SharedPreferences) {
    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(value) = prefs.edit().putBoolean(ENABLED, value).apply()

    var revealed: Boolean
        get() = prefs.getBoolean(REVEALED, false)
        set(value) = prefs.edit().putBoolean(REVEALED, value).apply()

    /**
     * The newest exit of this app's own process already written to the journal, so it is written once.
     * It doubles as the consent cutoff: Android keeps exits from before the switch was ever turned on,
     * and those are the one thing on this device that could be read backwards past consent.
     */
    var lastExit: Long
        get() = prefs.getLong(LAST_EXIT, 0L)
        set(value) = prefs.edit().putLong(LAST_EXIT, value).apply()

    /**
     * Collection begins now, so an exit recorded before this moment is not imported. Monotonic: the
     * cutoff only ever moves forward, which is what makes a disabled interval unimportable.
     */
    fun adoptConsent(now: Long) {
        if (now > lastExit) lastExit = now
    }

    private companion object {
        const val ENABLED = "collecting"
        const val REVEALED = "revealed"
        const val LAST_EXIT = "last_exit"
    }
}

/**
 * This process's diagnostics: the switch, the hidden surface, and the journal behind both.
 *
 * Collection is off until somebody turns it on, and nothing before that point is recorded, loaded or
 * counted. While it is on, the four observers below are attached once: playback sessions and playback
 * failures read from the app's own status flow, update outcomes read from the updater's public state,
 * and the crash and ANR records Android keeps for this package. Every one of those is a subscription
 * from outside; no audio-path, updater or core file learns that diagnostics exists.
 *
 * There is no transport. When this app has an endpoint, sending is one more line behind [collecting]
 * and none of the collection above changes.
 */
class Diagnostics(context: Context) {
    private val app = context.applicationContext
    private val prefs =
        DiagnosticsPrefs(app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE))
    private val journal =
        DiagnosticsJournal(
            store = JournalStore(File(app.filesDir, "diagnostics/journal.json")),
            enabled = { prefs.enabled },
        )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _collecting = MutableStateFlow(prefs.enabled)
    private val _revealed = MutableStateFlow(prefs.revealed)

    /** Whether the journal is recording, as the switch draws it. */
    val collecting: StateFlow<Boolean> = _collecting.asStateFlow()

    /** Whether the hidden surface is on screen in Settings. */
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    /** The caps the journal enforces, which the surface's copy states rather than restates. */
    val limits: JournalLimits
        get() = journal.limits

    private var runtime: NightfallRuntime? = null
    private var watching = false

    /** A configuration change builds the activity again; the process has started once. */
    @Volatile private var startupRecorded = false

    /** Hands the observers the app's status flow. Idempotent; called once from the activity. */
    fun attach(runtime: NightfallRuntime) {
        this.runtime = runtime
        if (_collecting.value) watch()
    }

    fun setCollecting(on: Boolean) {
        prefs.enabled = on
        // Consent starts at the switch, not at the beginning of Android's exit history: everything
        // Android recorded before this moment — and everything it records while the switch is off —
        // stays where it is.
        if (on) prefs.adoptConsent(System.currentTimeMillis())
        _collecting.value = on
        if (on) watch()
    }

    fun reveal() {
        prefs.revealed = true
        _revealed.value = true
    }

    fun hide() {
        prefs.revealed = false
        _revealed.value = false
    }

    /**
     * How long this process took to build its first screen, recorded once when the activity is first
     * composed. Measured from the process start Android reports, so it covers everything before the
     * app had a frame rather than only what Compose did.
     */
    fun recordStartup() {
        if (startupRecorded) return
        startupRecorded = true
        val elapsed = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        journal.record(JournalEvent(Journal.STARTUP, today(), bucket = Buckets.startup(elapsed)))
    }

    fun report(): String = journal.report(appVersion())

    /** True when this instance watches [context]'s process, which is what makes a singleton safe. */
    internal fun serves(context: Context): Boolean = app === context.applicationContext

    fun erase(): Boolean = journal.erase()

    /** The app's own identity, read from the installed package rather than from a build constant. */
    private fun appVersion(): AppVersion {
        var name = "unknown"
        var code = 0
        try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            name = info.versionName ?: name
            code =
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
                else @Suppress("DEPRECATION") info.versionCode
        } catch (e: Exception) {
            // A version that cannot be read is reported as unknown; the journal still works.
        }
        return AppVersion(name, code, Build.VERSION.SDK_INT)
    }

    private fun watch() {
        if (watching) return
        watching = true
        scope.launch { collectPlayback() }
        scope.launch { collectFailures() }
        scope.launch { collectUpdates() }
        scope.launch { recordExits() }
    }

    /**
     * One entry per finished playback session, holding how long it lasted in a bucket. A session the
     * process did not outlive leaves no entry, which is why the count is of finished sessions.
     *
     * The switch is part of this stream, not a filter after it: a session is only measured from the
     * moment collection was on. Night that started before the switch did is not attributed to it, and
     * neither is the part of a night that ran while it was off.
     */
    private suspend fun collectPlayback() {
        val source = runtime ?: return
        val sessions = SessionClock { SystemClock.elapsedRealtime() }
        combine(source.status.map { it.playing }.distinctUntilChanged(), _collecting) { playing, on ->
                playing to on
            }
            .distinctUntilChanged()
            .collect { (playing, on) ->
                if (!on) {
                    sessions.reset()
                    return@collect
                }
                val lasted = sessions.playing(playing) ?: return@collect
                journal.record(
                    JournalEvent(Journal.PLAYBACK, today(), bucket = Buckets.playback(lasted))
                )
            }
    }

    /** That sound failed, never why in the app's own words: those strings can carry a file path. */
    private suspend fun collectFailures() {
        val source = runtime ?: return
        source.status
            .map { it.error }
            .distinctUntilChanged()
            .collect { error ->
                if (error != null)
                    journal.record(
                        JournalEvent(
                            Journal.FAILURE,
                            today(),
                            detail = Buckets.PLAYBACK_FAILURE,
                        )
                    )
            }
    }

    /** Every update attempt and how it ended, read from the updater's public state, not from inside it. */
    private suspend fun collectUpdates() {
        var previous: String? = null
        UpdateSupport.of(app).state.collect { state ->
            val token =
                when (state) {
                    is UpdateState.Checking -> Buckets.CHECK
                    is UpdateState.UpToDate -> Buckets.UP_TO_DATE
                    is UpdateState.Available -> Buckets.AVAILABLE
                    is UpdateState.Downloading -> Buckets.DOWNLOAD
                    is UpdateState.ReadyToInstall -> Buckets.READY
                    is UpdateState.Installing -> Buckets.INSTALL
                    is UpdateState.Failed -> Buckets.FAILED
                    is UpdateState.RecentCheck -> Buckets.TOO_SOON
                    UpdateState.Idle -> null
                }
            if (token != null && token != previous)
                journal.record(JournalEvent(Journal.UPDATE, today(), detail = token))
            previous = token
        }
    }

    /**
     * Crashes, ANRs and the other ends Android recorded for this package since the last launch. This
     * is the only way an app can report its own death after the fact: the record is read from the
     * platform on the next start, after the process that died is gone.
     *
     * Only the reason and the date are kept. The record also carries a description, a process name
     * and a memory figure, and none of those exist in the journal.
     */
    private suspend fun recordExits() =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < 30) return@withContext
            val manager = app.getSystemService(ActivityManager::class.java) ?: return@withContext
            val recorded =
                try {
                    manager.getHistoricalProcessExitReasons(app.packageName, 0, 0)
                } catch (e: Exception) {
                    // Reading the platform's record is a courtesy; failing it changes nothing.
                    return@withContext
                }
            val seen = prefs.lastExit
            var newest = seen
            for (info in recorded.sortedBy { it.timestamp }) {
                if (info.timestamp <= seen) continue
                journal.record(
                    JournalEvent(
                        Journal.EXIT,
                        dateOf(info.timestamp),
                        detail = exitToken(info.reason),
                    )
                )
                newest = maxOf(newest, info.timestamp)
            }
            if (newest != seen) prefs.lastExit = newest
        }

    private fun exitToken(reason: Int): String =
        when (reason) {
            ApplicationExitInfo.REASON_ANR -> "anr"
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
            ApplicationExitInfo.REASON_SIGNALED -> "signaled"
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED -> "user"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "init_failure"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource_usage"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
            ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
            else -> "other"
        }
}

/**
 * Session lengths as a small state machine, so a night is only ever measured while collection was on.
 *
 * [reset] is what a switch turned off does: the run in progress is abandoned rather than paused, so
 * turning collection back on mid-night starts a fresh measurement at that moment instead of
 * reporting a length that mostly happened unwatched.
 */
internal class SessionClock(private val now: () -> Long) {
    private var startedAt: Long? = null

    /** True playback starts a session; false ends it and returns how long it was, or null if none ran. */
    fun playing(playing: Boolean): Long? {
        if (playing) {
            if (startedAt == null) startedAt = now()
            return null
        }
        val from = startedAt ?: return null
        startedAt = null
        return now() - from
    }

    /** Abandons the run in progress, so the next one is measured from where collection resumed. */
    fun reset() {
        startedAt = null
    }
}

/** One diagnostics per process, and a new Application object is a new process. */
object DiagnosticsSupport {
    @Volatile private var instance: Diagnostics? = null

    fun of(context: Context): Diagnostics {
        val application = context.applicationContext
        instance?.let { existing -> if (existing.serves(application)) return existing }
        return synchronized(this) {
            instance?.takeIf { it.serves(application) }
                ?: Diagnostics(application).also { instance = it }
        }
    }
}
