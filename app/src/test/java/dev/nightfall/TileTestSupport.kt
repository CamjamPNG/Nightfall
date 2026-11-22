// SPDX-License-Identifier: MIT
package dev.nightfall

import android.service.quicksettings.Tile
import org.robolectric.Shadows.shadowOf

/**
 * What the shade would draw, read from the tile service a test is listening through.
 *
 * The tile is the platform's own object and the service under test set every field on it; only
 * `updateTile` is shadowed, so what these read is what the service painted rather than a copy.
 */
internal fun NightfallTileService.shown(): Tile =
    checkNotNull(qsTile) { "the tile service answered no tile" }

/** The drawable the tile painted, by resource id: the glyph is what a tap will do. */
internal fun NightfallTileService.paintedGlyph(): Int = shadowOf(shown().icon).resId
