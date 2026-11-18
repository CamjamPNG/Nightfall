// SPDX-License-Identifier: MIT
package dev.nightfall.core

/**
 * Marks the tests that render a whole night of audio and take minutes rather than seconds.
 *
 * They always run in CI, where the gate lives. A local run can skip them with `-PwithoutNight` when
 * seven minutes is not what you wanted to spend on a one-line change.
 */
interface Night
