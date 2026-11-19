// SPDX-License-Identifier: MIT
package dev.nightfall

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decoder fallback, in the shape the platform imposes: one attempt covering `configure` **and**
 * `start`, because `csd-0` is submitted when the codec starts rather than when it configures.
 *
 * A fake here can fail at either call, which is the point — the fallback that existed before this
 * test wrapped only `configure`, and the stage a real decoder fails at is exactly what no device
 * available here can tell us.
 */
class OpusDecoderFallbackTest {
    private val header = ByteArray(19) { 1 }

    private fun attempt(failsAt: String?): Pair<List<String>, String> {
        val steps = ArrayList<String>()
        val result =
            openOpusDecoder(header) { offered ->
                steps += "create:${offered != null}"
                if (failsAt == "configure" && offered != null) error("csd-0 refused")
                steps += "configure"
                if (failsAt == "start" && offered != null) error("csd-0 refused at start")
                steps += "start"
                if (offered == null) "without-header" else "with-header"
            }
        return steps to result
    }

    @Test
    fun aDecoderThatAcceptsTheHeaderKeepsIt() {
        val (steps, result) = attempt(null)
        assertEquals("with-header", result)
        assertEquals(listOf("create:true", "configure", "start"), steps)
    }

    @Test
    fun aDecoderThatRefusesTheHeaderWhileConfiguringFallsBack() {
        val (steps, result) = attempt("configure")
        assertEquals("without-header", result)
        assertEquals(listOf("create:true", "create:false", "configure", "start"), steps)
    }

    @Test
    fun aDecoderThatRefusesTheHeaderWhenStartingFallsBack() {
        val (steps, result) = attempt("start")
        assertEquals("without-header", result)
        assertEquals(listOf("create:true", "configure", "create:false", "configure", "start"), steps)
    }
}
