// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The whole vocabulary of the diagnostics journal, and how much of it may be kept.
 *
 * The journal exists only after the person turns it on in the hidden surface, and it never leaves the
 * device unless they share the report themselves. So every value below is a closed set of tokens
 * rather than free text: an entry may say a night lasted two to eight hours, never that it lasted
 * four hours and eleven minutes, and never which mix was playing. A field that could pick one person
 * out of a crowd does not exist here — no path, no message text, no names.
 */
object Journal {
    /** Six kinds of entry, each naming what happened and never what was heard. */
    const val STARTUP = "startup"

    const val PLAYBACK = "playback"

    const val FAILURE = "failure"

    const val UPDATE = "update"

    const val EXIT = "exit"

    /**
     * The Opus decoder refused, and which step refused it. The detail is one of the tokens
     * `dev.nightfall.OpusDecodeFailure` defines — the decoder's own enum is the one definition of that
     * set, so a token cannot exist here that the code cannot produce.
     */
    const val DECODER = "decoder"
}

/**
 * One entry.
 *
 * [date] is the local date and there is no clock time beside it: when in the day somebody sleeps is
 * theirs, and an hour-of-night histogram is a far better fingerprint than anything this journal is
 * for. [detail] and [bucket] carry tokens from the closed sets in this file.
 */
@Serializable
data class JournalEvent(
    val kind: String,
    val date: String,
    val detail: String? = null,
    val bucket: String? = null,
)

/** How much a journal may hold. Past any of these the oldest entries are dropped first. */
data class JournalLimits(
    val entries: Int = 200,
    val bytes: Long = 64L * 1024,
    val days: Int = 30,
)

/**
 * Durations, coarse on purpose. A bucket is what makes a bug legible — "start-up is usually under a
 * second and sometimes ten or more" — while the exact number describes a person's routine.
 */
object Buckets {
    fun startup(ms: Long): String =
        when {
            ms < 1_000 -> "lt1s"
            ms < 2_000 -> "1to2s"
            ms < 5_000 -> "2to5s"
            ms < 10_000 -> "5to10s"
            else -> "gte10s"
        }

    fun playback(ms: Long): String =
        when {
            ms < 5 * 60_000L -> "lt5m"
            ms < 30 * 60_000L -> "5to30m"
            ms < 2 * 3_600_000L -> "30mto2h"
            ms < 8 * 3_600_000L -> "2to8h"
            else -> "gte8h"
        }

    /** How an update check ended. The failure's own message is never one of these: it can hold a URL. */
    const val CHECK = "check"
    const val UP_TO_DATE = "up_to_date"
    const val AVAILABLE = "available"
    const val DOWNLOAD = "download"
    const val READY = "ready"
    const val INSTALL = "install"
    const val FAILED = "failed"
    const val TOO_SOON = "too_soon"

    /**
     * A failure the app reported while starting or running sound. One token, not one per message:
     * the only structured fact the audio path exposes is that it failed, and its message text is
     * localized copy that can carry a file path, so a finer split would mean storing that text.
     */
    const val PLAYBACK_FAILURE = "playback"
}

/** The journal file's own shape on disk: the entries, oldest first, and nothing else. */
@Serializable
data class JournalFile(val schema: Int = 1, val events: List<JournalEvent> = emptyList())

/**
 * What the person reads and shares: the app's versions, what the journal covers, the limits in force,
 * how many entries there are of each kind, and the entries themselves. Pretty-printed, because a
 * report somebody cannot read is a report they cannot check before sending it.
 */
@Serializable
data class JournalDocument(
    val schema: Int = 1,
    val app: AppVersion,
    val range: DateRange,
    val limits: JournalLimitReport,
    val counts: Map<String, Int>,
    val events: List<JournalEvent>,
)

@Serializable
data class AppVersion(val versionName: String, val versionCode: Int, val androidSdk: Int)

@Serializable
data class DateRange(val first: String? = null, val last: String? = null, val entries: Int)

@Serializable
data class JournalLimitReport(val entries: Int, val bytes: Long, val days: Int)

/** Storage is one line shorter than the report; the report is the one a person has to be able to check. */
internal val compactJson = Json { encodeDefaults = false }

internal val reportJson = Json {
    encodeDefaults = false
    prettyPrint = true
}

/** Today's local date, the only date format the journal ever holds. */
internal fun today(): String = LocalDate.now().toString()

/** The local date [epochMillis] fell on, for a timestamp an Android API hands back. */
internal fun dateOf(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

/** The date an entry holds, or null when the file was edited by hand or written by a newer build. */
internal fun parseDay(date: String): LocalDate? =
    try {
        LocalDate.parse(date)
    } catch (e: DateTimeParseException) {
        null
    }
