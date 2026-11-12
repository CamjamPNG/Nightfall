// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class AudioAcceptanceTest {
    @Test
    fun renderAcceptanceClipAndMeasureBufferBudget() {
        val mixer = PcmMixer()
        mixer.sync(Presets.all.last())
        val frames = 1024
        val seconds = 24
        val blocks = 48000 * seconds / frames
        val bytes = ByteBuffer.allocate(44 + blocks * frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = FloatArray(frames * 2)
        var sum = 0.0
        var peak = 0f
        var renderNs = 0L
        var cpuNs = 0L
        val threadBean = java.lang.management.ManagementFactory.getThreadMXBean()
        var maxNs = 0L
        bytes
            .put("RIFF".toByteArray())
            .putInt(bytes.capacity() - 8)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(2)
            .putInt(48000)
            .putInt(192000)
            .putShort(4)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(bytes.capacity() - 44)
        repeat(blocks) {
            val t = System.nanoTime()
            val cpuStart = threadBean.currentThreadCpuTime
            mixer.render(pcm)
            val elapsed = System.nanoTime() - t
            cpuNs += threadBean.currentThreadCpuTime - cpuStart
            renderNs += elapsed
            maxNs = max(maxNs, elapsed)
            for (v in pcm) {
                assertTrue(v.isFinite() && abs(v) <= .981f)
                sum += v * v
                peak = max(peak, abs(v))
                bytes.putShort((v * 32767).toInt().toShort())
            }
        }
        val dir = File("../artifacts").apply { mkdirs() }
        File(dir, "nine-layer-dream.wav").writeBytes(bytes.array())
        val time = blocks * frames / 48000.0
        File(dir, "dsp-metrics.txt")
            .writeText(
                "Host JVM benchmark (not a Pixel 7 measurement)\nSample rate: 48000 Hz stereo\nSources: ${mixer.channelCount}\nDuration: $time seconds\nPeak: $peak\nRMS: ${sqrt(sum/(blocks*frames*2))}\nRender thread CPU time / audio duration: ${cpuNs/1e9/time*100}%\nRender wall time / audio duration: ${renderNs/1e9/time*100}%\nLargest render block: ${maxNs/1e6} ms (JIT/host scheduling included)\nBlock budget: ${frames/48.0} ms\nAll output finite and <= 0.981\n"
            )
        assertEquals(9, mixer.channelCount)
        assertTrue(peak > .005f)
    }

    @Test
    fun entireAvailableCatalogHasRealAudibleOutput() {
        val assetRoot = File("../app/src/main/assets/audio")
        val recordingLoader: (String) -> SampleData? = { id ->
            val file = File(assetRoot, "$id.pcm")
            if (!file.exists()) null
            else {
                Pcm16Data(ByteBuffer.wrap(file.readBytes()))
            }
        }
        for (s in Library.all.filter { it.available }) {
            val m = PcmMixer(recordings = recordingLoader)
            m.sync(Mix(layers = listOf(Library.layer(s.id, unique = "catalog-test"))))
            val b = FloatArray(2048)
            var energy = 0.0
            repeat(100) {
                m.render(b)
                for (v in b) {
                    assertTrue("Non-finite ${s.id}", v.isFinite())
                    energy += v * v
                }
            }
            assertTrue("Silent ${s.id}", energy > 1e-8)
        }
    }

    @Test
    fun noBundledRecordingLoopsBeforeThreeMinutes() {
        for (sound in Library.all.filter { it.kind == "recording" }) {
            val file = File("../app/src/main/assets/audio/${sound.asset}.pcm")
            assertTrue("Missing ${sound.id}", file.exists())
            assertTrue(
                "Short loop: ${sound.id}",
                RecordingSource.cycleSeconds((file.length() / 2).toInt()) >= 180.0,
            )
        }
    }
}
