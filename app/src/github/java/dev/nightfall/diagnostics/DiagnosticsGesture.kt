// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

/**
 * The reveal gesture: [required] taps in a row, all inside [windowMs] of the first one.
 *
 * The window starts at the first tap of a run rather than sliding with the last, so five taps spread
 * over ten seconds is not a slower way to the same place — it is four short runs that each ended. That
 * is what keeps an ordinary person from opening a diagnostics surface they never asked for, and it is
 * why this is a plain class with a clock handed to it rather than a side effect of a click handler.
 */
class TapGate(
    private val required: Int = 5,
    private val windowMs: Long = 3_000,
) {
    private var counted = 0
    private var startedAt = 0L

    /** True on the tap that completes a run. The next run starts from nothing. */
    fun tap(nowMs: Long): Boolean {
        if (counted == 0 || nowMs - startedAt > windowMs) {
            counted = 1
            startedAt = nowMs
            return false
        }
        counted++
        if (counted < required) return false
        counted = 0
        startedAt = 0L
        return true
    }
}
