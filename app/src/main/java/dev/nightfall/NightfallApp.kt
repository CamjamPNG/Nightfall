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

class NightfallApp : Application() {
    lateinit var runtime: NightfallRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = NightfallRuntime(this)
    }
}

data class PlaybackStatus(
    val playing: Boolean = false,
    val output: String = "System audio output",
    val remainingMs: Long = 0,
    val timerActive: Boolean = false,
    val timerPaused: Boolean = false,
    val timerFading: Boolean = false,
    val peak: Float = 0f,
    val load: Float = 0f,
    val underruns: Int = 0,
    val error: String? = null,
)

class NightfallRuntime(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Keep Application.onCreate cheap; catalogue construction/restoration belongs off main.
    private val _saved = MutableStateFlow(SavedState(current = Mix(name = "Your soundscape")))
    val saved = _saved.asStateFlow()
    val status = MutableStateFlow(PlaybackStatus())
    val ready = MutableStateFlow(false)
    val timer = SleepTimer()
    var host: AudioHost? = null
    var promotePlayback: (() -> Unit)? = null
    private val key = stringPreferencesKey("state_v1")
    private var persist: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val text = context.dataStore.data.first()[key]
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
                    status.value.copy(error = "Could not restore saved mix: ${e.message}")
            }
            ready.value = true
        }
    }

    fun change(transform: (SavedState) -> SavedState) {
        _saved.value = transform(_saved.value)
        persist?.cancel()
        persist =
            scope.launch {
                delay(250)
                val snapshot = _saved.value
                try {
                    context.dataStore.edit { it[key] = PresetJson.json.encodeToString(snapshot) }
                } catch (e: Exception) {
                    status.value = status.value.copy(error = "Saving failed: ${e.message}")
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
            status.value = status.value.copy(error = "Playback could not start: ${e.message}")
        }
    }

    fun pause() {
        host?.pause(true)
    }

    fun stop() {
        timer.cancel()
        host?.stop()
        status.value = status.value.copy(timerActive = false, remainingMs = 0, timerFading = false)
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
