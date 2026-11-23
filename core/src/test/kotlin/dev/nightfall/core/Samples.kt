// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The listening samples in `docs/samples/`: one recipe each, rendered for a few seconds through the
 * mixer the app runs.
 *
 * This is a documentation tool, not a test and not part of the app. It lives in the test source set
 * because the measurement helpers it reports with do, so nothing here can reach an APK, and it runs
 * the ordinary [PcmMixer] at the app's own rate and block size with offline recordings read from the
 * same bundled assets — the sample-rate conversion, `Levels` trim, filters, crossover, width, pan and
 * limiter are the ones a phone runs. What it cannot claim is that a phone produced these files: the
 * renderer is a JVM, no device decoder is involved, and nothing was recorded from a speaker.
 *
 * `tools/render_samples.sh` runs [main] and encodes what it writes; `docs/samples/README.md` owns the
 * published figures and [SampleClipTest] keeps them tied to what a render produces today.
 */
object Samples {
    /** The app's audio rate and block size, so a sample renders in the blocks the mixer really gets. */
    const val RATE = 48000
    const val BLOCK = 1024

    /**
     * Seconds of fade at each end of every clip: a night that starts mid-crackle clicks, and one that
     * stops at full level clicks twice. A raised cosine is silent at both ends and has no corner in
     * its slope, so the fade itself is not audible as a fade-in gesture.
     */
    const val FADE_SECONDS = 2.0

    /**
     * One clip. [id] is the file name in `docs/samples/` without its extension, and [peakDb] is the
     * peak of the rendered signal as it is published in that folder's README — [SampleClipTest]
     * compares a fresh render against it, which is what makes a stale clip a failure rather than a
     * quietly wrong file.
     */
    class Clip(
        val id: String,
        val title: String,
        /** What a person would have done in the app to hear this: a preset's name, or a typed phrase. */
        val recipe: String,
        val seconds: Double,
        val peakDb: Double,
        val mix: Mix,
    )

    /**
     * A preset exactly as it ships, including a themed night's plan: "Storm That Passes" is rendered
     * from second zero, where its opening phase is, so the clip is what the phone plays when a person
     * selects it.
     */
    private fun preset(id: String): Mix =
        Presets.all.firstOrNull { it.id == id } ?: error("no preset $id")

    /**
     * A mix from the phrase interpreter, which is the app's other way in. A phrase that is not read
     * cleanly — an unrecognised word, a substitution, a contradiction — would put a sound in the clip
     * that the document did not name, so it fails here instead.
     */
    private fun phrase(text: String): Mix {
        val plan = LocalPhraseInterpreter.interpret(text)
        require(plan.conflict == null) { "\"$text\" contradicts itself: ${plan.conflict}" }
        require(plan.unrecognized.isEmpty()) { "\"$text\" was not understood: ${plan.unrecognized}" }
        require(plan.substitutions.isEmpty()) { "\"$text\" was substituted: ${plan.substitutions}" }
        require(plan.layers.isNotEmpty()) { "\"$text\" asked for nothing" }
        return plan.toMix()
    }

    private fun clip(
        id: String,
        title: String,
        recipe: String,
        seconds: Double,
        peakDb: Double,
        mix: Mix,
    ) = Clip(id, title, recipe, seconds, peakDb, mix)

    /** Shortest to longest content, a mix at each scale: the whole catalogue, then single sources. */
    val all: List<Clip> =
        listOf(
            clip(
                "nine-layer-dream",
                "Nine-Layer Dream",
                "the Nine-Layer Dream preset",
                30.0,
                -11.13,
                preset("nine-layer-dream"),
            ),
            clip(
                "storm-that-passes",
                "Storm That Passes, opening phase",
                "the Storm That Passes themed night, from second zero",
                24.0,
                -15.29,
                preset("storm-that-passes"),
            ),
            clip(
                "rain-on-tent",
                "Rain on tent",
                "the phrase \"rain on a tent\"",
                20.0,
                -23.94,
                phrase("rain on a tent"),
            ),
            clip("ocean-waves", "Ocean waves", "the phrase \"ocean waves\"", 20.0, -18.74, phrase("ocean waves")),
            clip("campfires", "Campfires", "the phrase \"campfires\"", 20.0, -17.02, phrase("campfires")),
            clip("brown-noise", "Brown noise", "the phrase \"brown noise\"", 20.0, -15.13, phrase("brown noise")),
            clip(
                "lo-fi-sleep-music",
                "Lo-fi sleep music",
                "the phrase \"lo-fi sleep music\"",
                24.0,
                -20.55,
                phrase("lo-fi sleep music"),
            ),
            clip(
                "heavy-rain-metal-roof-fire",
                "Heavy rain on a metal roof, with a fire",
                "the phrase \"heavy rain on a metal roof with a fire\"",
                24.0,
                -14.32,
                phrase("heavy rain on a metal roof with a fire"),
            ),
        )

    /**
     * Renders [clip] and fades both ends. The mixer renders whole blocks, so the last block is
     * trimmed to the clip's exact length rather than padding the file.
     */
    fun render(clip: Clip, recordings: (String) -> SampleData? = M.assetLoader()): M.Signal {
        val mixer = PcmMixer(RATE, BLOCK, recordings)
        mixer.sync(clip.mix)
        val frames = (clip.seconds * RATE).roundToInt()
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        val buffer = FloatArray(BLOCK * 2)
        var written = 0
        while (written < frames) {
            mixer.render(buffer, BLOCK)
            val count = minOf(BLOCK, frames - written)
            for (i in 0 until count) {
                left[written + i] = buffer[i * 2]
                right[written + i] = buffer[i * 2 + 1]
            }
            written += count
        }
        fade(left)
        fade(right)
        return M.Signal(left, right)
    }

    private fun fade(channel: FloatArray) {
        val length = minOf((FADE_SECONDS * RATE).roundToInt(), channel.size / 2)
        for (i in 0 until length) {
            val gain = (.5 - .5 * cos(PI * i / length)).toFloat()
            channel[i] *= gain
            channel[channel.size - 1 - i] *= gain
        }
    }

    /** Peak of the louder channel, in dBFS: the level a listener's meter is asked about. */
    fun peakDb(signal: M.Signal): Double {
        var peak = 0f
        for (i in 0 until signal.frames) {
            peak = max(peak, max(abs(signal.left[i]), abs(signal.right[i])))
        }
        return 20.0 * log10(peak.toDouble())
    }

    /** The settings that produced a clip, written out so the document does not hold a second copy. */
    fun settings(mix: Mix): String =
        mix.layers.joinToString(", ") { "${it.soundId} at ${"%.2f".format(it.gain)}" } +
            "; master ${"%.2f".format(mix.master)}" +
            (if (mix.endless.enabled) "; endless night on" else "")

    /** A 48 kHz 16-bit stereo RIFF/WAVE file, the format the encoder is handed. */
    fun wav(signal: M.Signal): ByteArray {
        val frames = signal.frames
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .put("RIFF".toByteArray())
            .putInt(36 + frames * 4)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(2)
            .putInt(RATE)
            .putInt(RATE * 4)
            .putShort(4)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(frames * 4)
        for (i in 0 until frames) {
            bytes.putShort(pcm16(signal.left[i]))
            bytes.putShort(pcm16(signal.right[i]))
        }
        return bytes.array()
    }

    private fun pcm16(sample: Float): Short = (sample * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
}

/**
 * Writes every clip to WAV in the directory given as the first argument (or `build/samples`), printing
 * the figures `docs/samples/README.md` publishes. `tools/render_samples.sh` passes the directory and
 * then encodes each file; nothing here runs in CI.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/samples").apply { mkdirs() }
    println("clip                          seconds    peak dBFS    integrated LUFS    recipe")
    for (clip in Samples.all) {
        val signal = Samples.render(clip)
        File(out, "${clip.id}.wav").writeBytes(Samples.wav(signal))
        println(
            "%-29s %6.1f      %7.2f    %8.2f          %s".format(
                clip.id,
                clip.seconds,
                Samples.peakDb(signal),
                M.integratedLufs(signal),
                clip.recipe,
            )
        )
        println("    settings: ${Samples.settings(clip.mix)}")
    }
    println("WAV files in ${out.path}")
}
