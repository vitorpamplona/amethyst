/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Filesystem-backed `IEventStore`. Each event is stored as a JSON file under
 * `events/<aa>/<bb>/<id>.json` where `<aa><bb>` is the first 4 hex characters
 * of the event id. Writes are atomic (tmp + rename).
 *
 * This is the step-1 skeleton: only `insert`, `delete(id)`, and a minimal
 * `query` by event id. Indexes, tombstones, slots, FTS, vanish, expiration
 * sweep, and transactions arrive in later steps — see
 * `cli/plans/2026-04-24-file-event-store-*.md`.
 */
class FsEventStore(
    val root: Path,
) : IEventStore {
    private val eventsDir: Path = root.resolve(EVENTS_DIR)
    private val stagingDir: Path = root.resolve(STAGING_DIR)

    init {
        Files.createDirectories(eventsDir)
        Files.createDirectories(stagingDir)
        cleanStaging()
    }

    // ------------------------------------------------------------------
    // Insert
    // ------------------------------------------------------------------

    override fun insert(event: Event) {
        if (event.kind.isEphemeral()) return

        val canonical = canonicalPath(event.id)
        if (canonical.exists()) return

        Files.createDirectories(canonical.parent)
        val tmp = Files.createTempFile(stagingDir, event.id, JSON_EXT)
        try {
            Files.writeString(tmp, event.toJson())
            try {
                Files.move(tmp, canonical, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                // Racing writer installed the same id first. Canonical is
                // immutable — this is a no-op, matching SQLite's unique
                // constraint on event.id.
                Files.deleteIfExists(tmp)
            }
        } catch (t: Throwable) {
            Files.deleteIfExists(tmp)
            throw t
        }
    }

    override fun transaction(body: IEventStore.ITransaction.() -> Unit) {
        val txn =
            object : IEventStore.ITransaction {
                override fun insert(event: Event) = this@FsEventStore.insert(event)
            }
        txn.body()
    }

    // ------------------------------------------------------------------
    // Query (by id only — full planner arrives in step 2)
    // ------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> query(filter: Filter): List<T> {
        val out = mutableListOf<T>()
        query<T>(filter) { out.add(it) }
        return out
    }

    override fun <T : Event> query(filters: List<Filter>): List<T> {
        val seen = HashSet<HexKey>()
        val out = mutableListOf<T>()
        filters.forEach { f ->
            query<T>(f) { if (seen.add(it.id)) out.add(it) }
        }
        return out
    }

    override fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    ) {
        val ids = filter.ids ?: return // step-1: only id lookups are implemented
        @Suppress("UNCHECKED_CAST")
        ids.forEach { id ->
            readEvent(id)?.let { onEach(it as T) }
        }
    }

    override fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) {
        val seen = HashSet<HexKey>()
        filters.forEach { f ->
            query<T>(f) { if (seen.add(it.id)) onEach(it) }
        }
    }

    override fun count(filter: Filter): Int {
        var n = 0
        query<Event>(filter) { n++ }
        return n
    }

    override fun count(filters: List<Filter>): Int {
        var n = 0
        query<Event>(filters) { n++ }
        return n
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    override fun delete(filter: Filter) {
        val ids = filter.ids ?: return
        ids.forEach { delete(it) }
    }

    override fun delete(filters: List<Filter>) = filters.forEach(::delete)

    /** Delete an event by id. Returns 1 if a file was removed, 0 otherwise. */
    fun delete(id: HexKey): Int = if (canonicalPath(id).deleteIfExists()) 1 else 0

    override fun deleteExpiredEvents() {
        // Step-5 feature. No-op in skeleton — queries do not yet surface
        // expired events either, so nothing observable changes.
    }

    override fun close() {
        // No long-lived resources yet. The lock channel arrives in step 8.
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun canonicalPath(id: HexKey): Path {
        require(id.length >= 4) { "event id must be at least 4 hex chars, got '$id'" }
        return eventsDir.resolve(id.substring(0, 2)).resolve(id.substring(2, 4)).resolve("$id$JSON_EXT")
    }

    private fun readEvent(id: HexKey): Event? {
        val p = canonicalPath(id)
        if (!p.exists()) return null
        return try {
            Event.fromJson(p.readText())
        } catch (_: java.nio.file.NoSuchFileException) {
            null // file was removed between exists() and read — treat as absent
        }
    }

    private fun cleanStaging() {
        Files.list(stagingDir).use { stream ->
            stream.forEach { Files.deleteIfExists(it) }
        }
    }

    companion object {
        const val EVENTS_DIR = "events"
        const val STAGING_DIR = ".staging"
        const val JSON_EXT = ".json"
    }
}
