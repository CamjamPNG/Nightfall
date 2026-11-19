// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The journal on disk: one small private file, rewritten whole whenever it changes.
 *
 * Everything here is bounded. An entry older than [JournalLimits.days], or past the entry count, or
 * past the byte count once encoded, is dropped from the front of the list — oldest first — so the
 * journal cannot become a hoard on somebody's phone however long it is left switched on.
 */
class JournalStore(
    private val file: File,
    val limits: JournalLimits = JournalLimits(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    /**
     * What is on disk, or nothing when there is no file. A journal that cannot be decoded is treated
     * as an empty one: a diagnostics file is never allowed to be the reason the app fails.
     */
    fun load(): MutableList<JournalEvent> =
        try {
            if (!file.exists()) mutableListOf()
            else compactJson.decodeFromString<JournalFile>(file.readText()).events.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }

    /** Drops the oldest entries until the journal is inside all three limits. */
    fun trim(events: MutableList<JournalEvent>) {
        val oldestKept = today().minusDays(limits.days.toLong())
        events.removeAll { event ->
            val day = parseDay(event.date)
            day == null || day.isBefore(oldestKept)
        }
        while (events.size > limits.entries) events.removeAt(0)
        while (events.isNotEmpty() && encodedBytes(events) > limits.bytes) events.removeAt(0)
    }

    /** The file as it would be written, in bytes, which is what the byte cap is measured against. */
    fun encodedBytes(events: List<JournalEvent>): Long =
        compactJson.encodeToString(JournalFile(events = events)).toByteArray().size.toLong()

    /**
     * Writes through a temporary file and a rename, so an interrupted write leaves the previous
     * journal rather than half of a new one.
     */
    fun write(events: List<JournalEvent>) {
        val folder = file.parentFile ?: throw IOException("The journal has no folder: $file")
        folder.mkdirs()
        val temporary = File(folder, file.name + ".writing")
        temporary.writeText(compactJson.encodeToString(JournalFile(events = events)))
        if (file.exists() && !file.delete()) {
            temporary.delete()
            throw IOException("Could not replace $file")
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Could not write $file")
        }
    }

    /** Deletes the journal. True when no file is left, which is what "erased" has to mean. */
    fun erase(): Boolean {
        if (file.exists() && !file.delete()) return false
        return !file.exists()
    }
}

/**
 * The journal as the app uses it: an in-memory list with the same cap, flushed to [JournalStore] off
 * the audio thread.
 *
 * Two rules shape everything here. Recording is a no-op while the switch is off, so nothing is
 * written, held or counted before somebody turns it on. And a write is never attempted on the caller's
 * thread when the caller is the app: [runOnIo] defaults to one background thread, so a person's
 * screen, and the render loop behind it, never wait for a file.
 */
class DiagnosticsJournal(
    private val store: JournalStore,
    private val enabled: () -> Boolean,
    private val runOnIo: (Runnable) -> Unit = { work -> io.execute(work) },
) {
    private val lock = Any()

    /** Loaded on first use: an app that never turned this on never reads the file either. */
    private var events: MutableList<JournalEvent>? = null

    /** Adds one entry, then writes in the background. Does nothing at all while collection is off. */
    fun record(event: JournalEvent) {
        if (!enabled()) return
        synchronized(lock) {
            val list = loaded()
            list.add(event)
            store.trim(list)
        }
        runOnIo {
            synchronized(lock) {
                try {
                    store.write(loaded())
                } catch (e: IOException) {
                    // A journal that cannot be written is dropped, not retried: diagnostics may not
                    // become a recurring failure of the app it is watching.
                    events = mutableListOf()
                }
            }
        }
    }

    /** The caps in force, so the surface's own copy cannot drift from what is enforced here. */
    val limits: JournalLimits
        get() = store.limits

    /** The entries held now, oldest first. */
    fun entries(): List<JournalEvent> = synchronized(lock) { loaded().toList() }

    /**
     * Deletes the file and empties the list. Synchronous, because "erased" has to be true when it
     * returns — and repeated on the write thread, so a write already queued behind this call cannot
     * put the file back afterwards.
     */
    fun erase() {
        synchronized(lock) {
            events = mutableListOf()
            store.erase()
        }
        runOnIo { synchronized(lock) { store.erase() } }
    }

    /** The document a person reads and, if they choose, shares. */
    fun report(app: AppVersion): String {
        val listed = entries()
        return reportJson.encodeToString(
            JournalDocument(
                app = app,
                range = DateRange(listed.firstOrNull()?.date, listed.lastOrNull()?.date, listed.size),
                limits =
                    JournalLimitReport(
                        entries = store.limits.entries,
                        bytes = store.limits.bytes,
                        days = store.limits.days,
                    ),
                counts = listed.groupingBy { it.kind }.eachCount().toSortedMap(),
                events = listed,
            )
        )
    }

    /** Caller holds [lock]. */
    private fun loaded(): MutableList<JournalEvent> =
        events
            ?: store.load()
                .also {
                    store.trim(it)
                    events = it
                }

    private companion object {
        /**
         * One daemon thread for every journal write in the process. Daemon so a test JVM whose last
         * test wrote an entry still exits.
         */
        val io: java.util.concurrent.ExecutorService =
            Executors.newSingleThreadExecutor { work ->
                Thread(work, "nightfall-diagnostics").apply { isDaemon = true }
            }
    }
}
