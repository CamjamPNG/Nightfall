// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@Serializable
data class MusicConfig(
    val tuning: Float = 440f,
    val key: Int = 0,
    val minor: Boolean = false,
    val tempo: Float = 48f,
    val density: Float = .4f,
    val complexity: Float = .45f,
    val reverb: Float = .35f,
    val delay: Float = .2f,
    val percussion: Boolean = false,
    val randomness: Float = .4f,
    val melody: Boolean = true,
    val texture: Int = 0,
    val seed: Long = 42L,
)

@Serializable
data class Layer(
    val id: String,
    val soundId: String,
    val name: String,
    val gain: Float = .35f,
    val enabled: Boolean = true,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val pan: Float = 0f,
    val width: Float = .7f,
    val highPass: Float = 20f,
    val lowPass: Float = 18000f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val brightness: Float = .5f,
    val tilt: Float = 0f,
    val intensity: Float = .5f,
    val eventRate: Float = .4f,
    val modulation: Float = .2f,
    val fadeSeconds: Float = 2f,
    val stopAfterMinutes: Float = 0f,
    val weatherToRainMinutes: Float = 0f,
    val music: MusicConfig = MusicConfig(),
)

@Serializable
data class Mix(
    val id: String = "current",
    val name: String = "Nine-Layer Dream",
    val layers: List<Layer> = emptyList(),
    val master: Float = .5f,
    val favorite: Boolean = false,
    /**
     * Endless night, off by default. A mix saved before this existed decodes as off, which is the
     * behaviour it was saved with: the layers exactly as set, and nothing evolving.
     */
    val endless: EndlessConfig = EndlessConfig(),
)

/**
 * This mix with new layer ids, and every phase target following them.
 *
 * A themed night's plan is written in layer ids, so renaming the layers without renaming the plan
 * leaves a night whose every phase target misses: the layers stay unnamed, and an unnamed layer plays
 * its own gain all night, so the plan quietly stops existing. Applying a preset renames the layers on
 * purpose — a whole mix crossfades in rather than mutating the channels in place — so this is the one
 * way to rename them, and `NightfallRuntime.usePreset` is its caller.
 */
fun Mix.reidentify(fresh: (Layer) -> String): Mix {
    val ids = layers.associate { it.id to fresh(it) }
    return copy(
        layers = layers.map { it.copy(id = ids.getValue(it.id)) },
        endless =
            endless.copy(
                phases =
                    endless.phases.map { phase ->
                        phase.copy(
                            layers =
                                phase.layers.map { entry ->
                                    entry.copy(layerId = ids[entry.layerId] ?: entry.layerId)
                                }
                        )
                    }
            ),
    )
}

@Serializable
data class Settings(
    val light: Boolean = false,
    val reducedMotion: Boolean = false,
    val ducking: Boolean = true,
    val dynamicWeather: Boolean = true,
    val slowVariation: Boolean = true,
    val lowDistraction: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val fadeSeconds: Float = 20f,
)

@Serializable
data class SavedState(
    val current: Mix = Presets.all.last(),
    val presets: List<Mix> = emptyList(),
    val settings: Settings = Settings(),
    val favoriteIds: Set<String> = emptySet(),
)

object PresetJson {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = false
    }

    fun encode(mix: Mix): String = json.encodeToString(mix)

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun decodeStream(input: java.io.InputStream): Mix = sanitize(json.decodeFromStream<Mix>(input))

    fun decode(value: String): Mix = sanitize(json.decodeFromString<Mix>(value))

    fun sanitize(mix: Mix): Mix {
        require(mix.name.isNotBlank() && mix.name.length <= 200) {
            "Mix needs a name of 1–200 characters"
        }
        require(mix.master.isFinite())
        val ids = HashSet<String>()
        return mix.copy(
            master = mix.master.coerceIn(0f, 1f),
            endless = nights(mix.endless),
            layers =
                mix.layers.map { l ->
                    require(ids.add(l.id) && l.id.length <= 200) { "Duplicate or invalid layer ID" }
                    require(Library.byId.containsKey(l.soundId)) { "Unknown sound: ${l.soundId}" }
                    require(Library.byId.getValue(l.soundId).available) {
                        "Recording not bundled: ${l.soundId}"
                    }
                    val floats =
                        listOf(
                            l.gain,
                            l.pan,
                            l.width,
                            l.highPass,
                            l.lowPass,
                            l.bass,
                            l.mid,
                            l.treble,
                            l.brightness,
                            l.tilt,
                            l.intensity,
                            l.eventRate,
                            l.modulation,
                            l.fadeSeconds,
                            l.stopAfterMinutes,
                            l.weatherToRainMinutes,
                            l.music.tuning,
                            l.music.tempo,
                            l.music.density,
                            l.music.complexity,
                            l.music.reverb,
                            l.music.delay,
                            l.music.randomness,
                        )
                    require(floats.all { it.isFinite() }) { "Non-finite audio setting" }
                    l.copy(
                        gain = l.gain.coerceIn(0f, 1f),
                        pan = l.pan.coerceIn(-1f, 1f),
                        width = l.width.coerceIn(0f, 1f),
                        highPass = l.highPass.coerceIn(10f, 4000f),
                        lowPass = l.lowPass.coerceIn(200f, 20000f),
                        bass = l.bass.coerceIn(-12f, 12f),
                        mid = l.mid.coerceIn(-12f, 12f),
                        treble = l.treble.coerceIn(-12f, 12f),
                        brightness = l.brightness.coerceIn(0f, 1f),
                        tilt = l.tilt.coerceIn(-1f, 1f),
                        intensity = l.intensity.coerceIn(0f, 1f),
                        eventRate = l.eventRate.coerceIn(0f, 1f),
                        modulation = l.modulation.coerceIn(0f, 1f),
                        fadeSeconds = l.fadeSeconds.coerceIn(.05f, 120f),
                        stopAfterMinutes = l.stopAfterMinutes.coerceIn(0f, 1440f),
                        weatherToRainMinutes = l.weatherToRainMinutes.coerceIn(0f, 1440f),
                        music =
                            l.music.copy(
                                tuning = l.music.tuning.coerceIn(400f, 480f),
                                key = l.music.key.mod(12),
                                tempo = l.music.tempo.coerceIn(20f, 90f),
                                density = l.music.density.coerceIn(0f, 1f),
                                complexity = l.music.complexity.coerceIn(0f, 1f),
                                reverb = l.music.reverb.coerceIn(0f, 1f),
                                delay = l.music.delay.coerceIn(0f, 1f),
                                randomness = l.music.randomness.coerceIn(0f, 1f),
                                texture = l.music.texture.coerceIn(0, 4),
                            ),
                    )
                },
        )
    }

    /** A themed night's plan, clamped to the times and shares the schedule can evaluate. */
    private fun nights(config: EndlessConfig): EndlessConfig {
        if (config.phases.isEmpty()) return config
        val phases =
            config.phases
                .filter { it.startMinutes.isFinite() }
                .map { phase ->
                    phase.copy(
                        startMinutes = phase.startMinutes.coerceIn(0f, 1440f),
                        layers =
                            phase.layers
                                .filter { it.layerId.length <= 200 && it.level.isFinite() }
                                .map { it.copy(level = it.level.coerceIn(0f, 1f)) },
                    )
                }
                .sortedBy { it.startMinutes }
        // Two phases at the same minute are one phase, and the later entry wins. Keeping both would
        // give the first a zero-length transition, which is a step, and dropping the earlier one is
        // the only reading of "at minute 30, play this" that a person could have meant.
        val distinct = ArrayList<NightPhase>(phases.size)
        for (phase in phases) {
            if (distinct.isNotEmpty() && distinct.last().startMinutes == phase.startMinutes)
                distinct[distinct.size - 1] = phase
            else distinct.add(phase)
        }
        return config.copy(phases = distinct)
    }
}

/** Uses only monotonic time. Paused time does not consume the duration. */
class SleepTimer {
    @Volatile private var deadline = 0L
    @Volatile private var remaining = 0L
    @Volatile
    var active = false
        private set

    @Volatile
    var paused = false
        private set

    @Volatile
    var fadeMs = 20000L
        private set

    fun start(now: Long, durationMs: Long, fadeDurationMs: Long) {
        require(durationMs > 0)
        remaining = durationMs
        deadline = now + durationMs
        fadeMs = fadeDurationMs.coerceAtLeast(1)
        active = true
        paused = false
    }

    fun pause(now: Long) {
        if (active && !paused) {
            // Preserve negative time as progress into the final fade.
            remaining = deadline - now
            paused = true
        }
    }

    fun resume(now: Long) {
        if (active && paused) {
            deadline = now + remaining
            paused = false
        }
    }

    fun cancel() {
        active = false
        paused = false
        remaining = 0
    }

    fun remainingMs(now: Long): Long =
        if (!active) 0
        else if (paused) remaining.coerceAtLeast(0) else (deadline - now).coerceAtLeast(0)

    fun gain(now: Long): Float =
        if (!active) 1f
        else if (paused) (1.0 + remaining.toDouble() / fadeMs).toFloat().coerceIn(0f, 1f)
        else if (now <= deadline) 1f
        else (1.0 - (now - deadline).toDouble() / fadeMs).toFloat().coerceIn(0f, 1f)

    fun expired(now: Long) = active && !paused && now >= deadline + fadeMs
}
