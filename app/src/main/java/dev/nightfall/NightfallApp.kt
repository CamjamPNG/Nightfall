// SPDX-License-Identifier: MIT
package dev.nightfall

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import dev.nightfall.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.dataStore by preferencesDataStore("nightfall")

private val stateKey = stringPreferencesKey("state_v1")

class NightfallApp : Application() {
    lateinit var runtime: NightfallRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = NightfallRuntime(this)
    }
}

/**
 * How audio is leaving the device. Typed, so the card's icon and the Settings line never depend on
 * the wording of a label: the previous string forced a `startsWith("Bluetooth")` test for the icon
 * and a `substringBefore(" · ")` for the short form, recovering facts this class now holds.
 */
data class OutputRoute(val kind: Kind = Kind.SYSTEM, val deviceName: String = "") {
    enum class Kind {
        /** No device reported, or one with no name. */
        SYSTEM,
        /** A named device of a type this app does not recognise: its name is the whole label. */
        DEVICE,
        BLUETOOTH,
        WIRED,
        SPEAKER,
    }
}

data class PlaybackStatus(
    val playing: Boolean = false,
    /**
     * Whether the mix has been reset rather than paused. A widget and the transport control both
     * have to tell "silent but resumable" from "nothing loaded", and only the runtime knows: stop
     * resets the mixer, pause does not. Nothing else reads this, so it is not a second state to keep
     * in sync with [playing].
     */
    val stopped: Boolean = true,
    val output: OutputRoute = OutputRoute(),
    val remainingMs: Long = 0,
    val timerActive: Boolean = false,
    val timerPaused: Boolean = false,
    val timerFading: Boolean = false,
    val peak: Float = 0f,
    val load: Float = 0f,
    val underruns: Int = 0,
    val error: String? = null,
)

class NightfallRuntime(
    val context: Context,
    /**
     * Writes one serialized state to storage. A parameter rather than a direct DataStore call so a
     * test can hold a write open and prove what happens to an edit that arrives while it is still
     * being written; the app always passes nothing and writes through DataStore.
     */
    private val write: suspend (String) -> Unit = { text -> context.dataStore.edit { it[stateKey] = text } },
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Keep Application.onCreate cheap; catalogue construction/restoration belongs off main.
    // A mix name that gets written to storage, not drawn copy: extracting it means deciding what an
    // existing save migrates to, which docs/LOCALIZATION.md keeps in its own stage.
    private val _saved = MutableStateFlow(SavedState(current = Mix(name = "Your soundscape")))
    val saved = _saved.asStateFlow()
    val status = MutableStateFlow(PlaybackStatus())
    val ready = MutableStateFlow(false)
    val timer = SleepTimer()
    var host: AudioHost? = null
    var promotePlayback: (() -> Unit)? = null
    private var persist: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val text = context.dataStore.data.first()[stateKey]
                if (text != null) {
                    val loaded = PresetJson.json.decodeFromString<SavedState>(text)
                    _saved.value =
                        loaded.copy(
                            current = PresetJson.sanitize(loaded.current),
                            presets = loaded.presets.map(PresetJson::sanitize),
                        )
                } else {
                    _saved.value = SavedState()
                }
            } catch (e: Exception) {
                _saved.value = SavedState()
                status.value =
                    status.value.copy(error = context.getString(R.string.error_restore_failed, e.message))
            }
            ready.value = true
        }
    }

    fun change(transform: (SavedState) -> SavedState) {
        _saved.value = transform(_saved.value)
        persist?.cancel()
        persist =
            scope.launch {
                // Only the debounce is cancellable. Once it expires the next edit supersedes the
                // value written here, not the write: tearing a write out mid-save on a slow device
                // reported the app's own cancellation as a "Saving failed" banner.
                delay(250)
                withContext(NonCancellable) {
                    try {
                        write(PresetJson.json.encodeToString(_saved.value))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status.value =
                            status.value.copy(
                                error = context.getString(R.string.error_save_failed, e.message)
                            )
                    }
                }
            }
    }

    fun mix(transform: (Mix) -> Mix) = change { it.copy(current = transform(it.current)) }

    fun layer(id: String, transform: (Layer) -> Layer) = mix {
        it.copy(layers = it.layers.map { l -> if (l.id == id) transform(l) else l })
    }

    fun add(sound: Sound, music: MusicConfig? = null) {
        if (!sound.available) return
        mix {
            it.copy(
                layers =
                    it.layers +
                        Library.layer(sound.id).let { l ->
                            if (music == null) l else l.copy(music = music)
                        }
            )
        }
    }

    fun reorder(id: String, delta: Int) = mix { m ->
        val list = m.layers.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from >= 0) {
            val to = (from + delta).coerceIn(0, list.lastIndex)
            list.add(to, list.removeAt(from))
        }
        m.copy(layers = list)
    }

    fun usePreset(p: Mix) {
        mix {
            p.copy(
                id = "current",
                layers = p.layers.map { it.copy(id = UUID.randomUUID().toString()) },
            )
        }
        if (!status.value.playing) play()
    }

    fun saveMix(name: String) {
        val preset =
            _saved.value.current.copy(
                id = UUID.randomUUID().toString(),
                // Persisted as the mix's name, so this is data rather than copy for the same reason
                // as the default above.
                name = name.trim().ifEmpty { "My night mix" },
            )
        change {
            it.copy(current = it.current.copy(name = preset.name), presets = it.presets + preset)
        }
    }

    fun savePreset(p: Mix) = change {
        it.copy(
            presets = it.presets + PresetJson.sanitize(p).copy(id = UUID.randomUUID().toString())
        )
    }

    fun updatePreset(id: String, transform: (Mix) -> Mix) = change {
        it.copy(presets = it.presets.map { p -> if (p.id == id) transform(p) else p })
    }

    fun deletePreset(id: String) = change {
        it.copy(presets = it.presets.filterNot { p -> p.id == id })
    }

    fun play() {
        if (!ready.value) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java).setAction("play"),
            )
        } catch (e: Exception) {
            status.value = status.value.copy(error = context.getString(R.string.error_playback_start_failed, e.message))
        }
    }

    fun pause() {
        host?.pause(true)
    }

    fun stop() {
        timer.cancel()
        host?.stop()
        status.value =
            status.value.copy(
                playing = false,
                stopped = true,
                timerActive = false,
                remainingMs = 0,
                timerFading = false,
            )
    }

    fun setTimer(minutes: Long) {
        if (minutes <= 0) timer.cancel()
        else
            timer.start(
                android.os.SystemClock.elapsedRealtime(),
                minutes * 60000,
                (_saved.value.settings.fadeSeconds * 1000).toLong(),
            )
        if (!status.value.playing) timer.pause(android.os.SystemClock.elapsedRealtime())
        status.value =
            status.value.copy(
                timerActive = timer.active,
                remainingMs = timer.remainingMs(android.os.SystemClock.elapsedRealtime()),
            )
    }
}

class NightfallViewModel(app: Application) : AndroidViewModel(app) {
    val runtime = (app as NightfallApp).runtime
    val saved = runtime.saved
    val status = runtime.status
}
