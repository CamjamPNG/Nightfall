// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class MixerTest {
    private fun advance(m: PcmMixer, blocks: Int = 150): FloatArray {
        val b = FloatArray(2048)
        repeat(blocks) { m.render(b) }
        return b
    }

    private fun rms(a: FloatArray) = sqrt(a.sumOf { it.toDouble() * it } / a.size)

    @Test
    fun nineSourcesAreSimultaneousAndIndependentlyAudible() {
        val mix = Presets.all.last()
        assertEquals(9, mix.layers.size)
        val mixer = PcmMixer()
        mixer.sync(mix)
        val out = advance(mixer)
        assertEquals(9, mixer.channelCount)
        assertTrue(rms(out) > .005)
        mix.layers.forEach { layer ->
            val m = PcmMixer()
            m.sync(mix.copy(layers = listOf(layer)))
            assertTrue("${layer.name} is silent", rms(advance(m)) > .00001)
        }
    }

    @Test
    fun gainIsIndependentAndMasterScales() {
        val mix = Presets.all[3]
        val m = PcmMixer()
        m.sync(mix)
        advance(m)
        m.sync(
            mix.copy(
                layers = mix.layers.mapIndexed { i, l -> if (i == 0) l.copy(gain = .1f) else l }
            )
        )
        advance(m)
        assertEquals(.1f, m.layerGain(mix.layers[0].id), .001f)
        assertEquals(.25f, m.layerGain(mix.layers[1].id), .001f)
        val a = PcmMixer()
        val b = PcmMixer()
        a.sync(mix.copy(master = .25f))
        b.sync(mix.copy(master = .5f))
        val aa = advance(a)
        val bb = advance(b)
        assertEquals(2.0, rms(bb) / rms(aa), .03)
    }

    @Test
    fun muteSoloAndEnableLogic() {
        val mix = Presets.all[3]
        val m = PcmMixer()
        m.sync(mix.copy(layers = mix.layers.map { it.copy(mute = true) }))
        assertEquals(0.0, rms(advance(m)), 0.0)
        m.sync(mix.copy(layers = mix.layers.mapIndexed { i, l -> l.copy(solo = i == 0) }))
        advance(m)
        assertTrue(m.layerGain(mix.layers[0].id) > .1f)
        assertEquals(0f, m.layerGain(mix.layers[1].id), .0001f)
        m.sync(mix.copy(layers = mix.layers.map { it.copy(enabled = false) }))
        assertTrue(rms(advance(m)) < .00001)
    }

    @Test
    fun dynamicAdditionRemovalPreservesOtherSourcePhase() {
        val base = Mix(layers = listOf(Library.layer("brown", unique = "brown")))
        val a = PcmMixer()
        val b = PcmMixer()
        a.sync(base)
        b.sync(base)
        advance(a)
        advance(b)
        val extra = Library.layer("pink", gain = 0f, unique = "pink")
        a.sync(base.copy(layers = base.layers + extra))
        val aa = advance(a, 10)
        val bb = advance(b, 10)
        assertArrayEquals(aa, bb, 0f)
        a.sync(base)
        assertEquals(1, a.channelCount)
        assertArrayEquals(advance(a, 120), advance(b, 120), 0f)
    }

    @Test
    fun removalAndGainChangesAreSmoothed() {
        val mix =
            Mix(
                layers =
                    listOf(
                        Library.layer("440-hz-ambient-music", unique = "pad")
                            .copy(fadeSeconds = .2f)
                    )
            )
        val m = PcmMixer()
        m.sync(mix)
        val before = advance(m, 200)
        m.sync(mix.copy(layers = emptyList()))
        val after = advance(m, 1)
        assertTrue(abs(after[0] - before[before.size - 2]) < .03f)
        assertEquals(0.0, rms(advance(m, 30)), .00001)
    }

    @Test
    fun limiterPreventsClippingWithLargeMix() {
        for (x in listOf(-1000f, -4f, -1f, 0f, 1f, 4f, 1000f, Float.NaN)) assertTrue(
            abs(PcmMixer.limit(x)) <= .981f
        )
        val m = PcmMixer()
        m.sync(Mix(master = 1f, layers = List(100) { Library.layer("white", 1f, "$it") }))
        val a = advance(m, 10)
        assertTrue(a.all { it.isFinite() && abs(it) <= .981f })
        assertTrue(rms(a) > .01)
    }

    @Test
    fun colorsAreDeterministicFiniteAndSpectrallyDifferent() {
        val differences = mutableListOf<Double>()
        for (c in 0..10) {
            val n = Noise(48000, 42)
            val n2 = Noise(48000, 42)
            var prev = 0f
            var e = 0.0
            var diff = 0.0
            repeat(96000) {
                val x = n.next(c)
                assertEquals(x, n2.next(c), 0f)
                assertTrue(x.isFinite())
                e += x * x
                diff += (x - prev) * (x - prev)
                prev = x
            }
            assertTrue(e > 0)
            differences += diff / e
        }
        assertTrue(differences[2] < differences[1])
        assertTrue(differences[1] < differences[0])
        assertTrue(differences[0] < differences[4])
        assertTrue(differences[0] < differences[5])
    }

    @Test
    fun correctTuningAndMusicalReproducibility() {
        assertEquals(432.0, MusicSource.frequency(69, 432f), .00001)
        assertEquals(880.0, MusicSource.frequency(81, 440f), .00001)
        val mix = Presets.all[8]
        val a = PcmMixer()
        val b = PcmMixer()
        a.sync(mix)
        b.sync(mix)
        assertArrayEquals(advance(a, 300), advance(b, 300), 0f)
    }

    @Test
    fun timerStartPauseResumeFadeExpiration() {
        val t = SleepTimer()
        t.start(100, 10000, 2000)
        assertEquals(9000, t.remainingMs(1100))
        t.pause(1100)
        assertEquals(9000, t.remainingMs(100000))
        t.resume(100000)
        assertEquals(1f, t.gain(109000), 0f)
        assertEquals(.5f, t.gain(110000), .001f)
        assertTrue(t.expired(111000))
        t.cancel()
        assertFalse(t.active)
    }

    @Test
    fun layerFadeoutDoesNotStopOtherLayers() {
        val l =
            Library.layer("pink", unique = "fade").copy(stopAfterMinutes = .01f, fadeSeconds = .1f)
        val m = PcmMixer()
        m.sync(Mix(layers = listOf(l)))
        assertEquals(0.0, rms(advance(m, 60)), .00001)
        val n = PcmMixer()
        n.sync(Mix(layers = listOf(l, Library.layer("brown", unique = "keep"))))
        assertTrue(rms(advance(n, 60)) > .001)
    }

    @Test
    fun jsonRoundTripsEverySettingAndRejectsInvalidImports() {
        val m =
            Presets.all
                .last()
                .copy(
                    layers =
                        Presets.all.last().layers.map { it.copy(pan = .4f, stopAfterMinutes = 30f) }
                )
        assertEquals(m, PresetJson.decode(PresetJson.encode(m)))
        val saved = SavedState(m, listOf(m), Settings(light = true))
        assertEquals(
            saved,
            PresetJson.json.decodeFromString<SavedState>(PresetJson.json.encodeToString(saved)),
        )
        try {
            PresetJson.decode(
                """{"name":"Bad","layers":[{"id":"a","soundId":"unknown","name":"Bad"}]}"""
            )
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun stopResetsSessionAndThereIsNoNineLayerCap() {
        val m = PcmMixer()
        val mix = Presets.all.last()
        m.sync(mix)
        advance(m)
        m.reset()
        m.sync(mix)
        val fresh = PcmMixer()
        fresh.sync(mix)
        assertArrayEquals(advance(fresh), advance(m), 0f)
        m.sync(Mix(layers = List(512) { Library.layer("brown", unique = "many-$it") }))
        m.render(FloatArray(2048))
        assertEquals(512, m.channelCount)
    }

    @Test
    fun pausingDuringFinalFadePreservesItsLevelAndRemainingFadeTime() {
        val timer = SleepTimer()
        timer.start(0, 1000, 2000)
        assertEquals(.5f, timer.gain(2000), .001f)
        timer.pause(2000)
        assertEquals(.5f, timer.gain(100000), .001f)
        assertEquals(0, timer.remainingMs(100000))
        assertFalse(timer.expired(100000))
        timer.resume(100000)
        assertEquals(.5f, timer.gain(100000), .001f)
        assertFalse(timer.expired(100999))
        assertTrue(timer.expired(101000))
    }

    @Test
    fun duplicateNoiseLayersHaveIndependentRandomStreams() {
        val a = PcmMixer()
        val b = PcmMixer()
        a.sync(Mix(layers = listOf(Library.layer("brown", unique = "brown-a"))))
        b.sync(Mix(layers = listOf(Library.layer("brown", unique = "brown-b"))))
        val left = advance(a)
        val right = advance(b)
        assertTrue(left.indices.any { abs(left[it] - right[it]) > .0001f })
    }

    @Test
    fun expiredLayersStopReadingSamplesAndCanResumeWithANewSchedule() {
        var reads = 0
        val data =
            object : SampleData {
                override val size = 24000 * 190

                override fun get(index: Int): Float {
                    reads++
                    return .25f
                }
            }
        val layer =
            Library.layer("forest-birds", unique = "scheduled-recording")
                .copy(stopAfterMinutes = .002f, fadeSeconds = .05f)
        val mixer = PcmMixer(recordings = { data })
        mixer.sync(Mix(layers = listOf(layer)))
        advance(mixer, 10)
        val readsWhenStopped = reads
        assertTrue(readsWhenStopped > 0)
        advance(mixer, 100)
        assertEquals(readsWhenStopped, reads)
        mixer.sync(Mix(layers = listOf(layer.copy(stopAfterMinutes = 0f))))
        advance(mixer, 5)
        assertTrue(reads > readsWhenStopped)
    }
}
