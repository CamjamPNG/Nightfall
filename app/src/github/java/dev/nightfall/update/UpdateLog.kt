// SPDX-License-Identifier: MIT
package dev.nightfall.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One release as the manifest's `log` describes it: the version, the day it shipped and a few
 * plain lines of what changed. This is the app's whole changelog — Preferences store the copy, so
 * the "What's new" view never makes a request of its own.
 */
@Serializable
data class UpdateLogEntry(
    val version: String = "",
    val date: String = "",
    val notes: List<String> = emptyList(),
)

/**
 * Reads the release log. None of it decides an install, so anything malformed is dropped instead of
 * refusing the update: a hostile or broken `log` costs the notes, never the release. Everything the
 * view draws is bounded here, which is also what makes a corrupted Preferences copy harmless.
 */
object UpdateLogParser {
    const val MAX_ENTRIES = 8
    const val MAX_NOTES = 4
    const val MAX_NOTE_CHARS = 160

    private val VERSION = Regex("""\d+\.\d+(?:\.\d+){0,2}""")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun parse(element: JsonElement?): List<UpdateLogEntry> {
        val entries = element as? JsonArray ?: return emptyList()
        val seen = HashSet<String>()
        val log = ArrayList<UpdateLogEntry>(minOf(entries.size, MAX_ENTRIES))
        for (entry in entries) {
            if (log.size == MAX_ENTRIES) break
            val obj = entry as? JsonObject ?: continue
            val version = string(obj["version"]) ?: continue
            if (!VERSION.matches(version) || !seen.add(version)) continue
            val notes =
                ((obj["notes"] as? JsonArray) ?: JsonArray(emptyList()))
                    .mapNotNull { note -> string(note)?.let(::plain) }
                    .distinct()
                    .take(MAX_NOTES)
            log += UpdateLogEntry(version, string(obj["date"])?.takeIf { DATE.matches(it) } ?: "", notes)
        }
        return log
    }

    /**
     * The same bounds applied to a log read back from disk: a Preferences copy that was truncated or
     * edited by something else still cannot draw an unbounded or malformed view.
     */
    fun sanitize(entries: List<UpdateLogEntry>): List<UpdateLogEntry> {
        val seen = HashSet<String>()
        return entries
            .filter { VERSION.matches(it.version) && seen.add(it.version) }
            .take(MAX_ENTRIES)
            .map { entry ->
                entry.copy(
                    date = entry.date.takeIf { DATE.matches(it) } ?: "",
                    notes = entry.notes.mapNotNull(::plain).distinct().take(MAX_NOTES),
                )
            }
    }

    /** One line the app can draw: no control characters, no runs of space, bounded length. */
    fun plain(text: String): String? {
        val folded =
            buildString(text.length) {
                text.forEach { character -> append(if (character < ' ' || character == '\u007f') ' ' else character) }
            }
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        if (folded.isEmpty()) return null
        return if (folded.length <= MAX_NOTE_CHARS) folded else folded.take(MAX_NOTE_CHARS - 1).trimEnd() + "…"
    }

    private fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}
