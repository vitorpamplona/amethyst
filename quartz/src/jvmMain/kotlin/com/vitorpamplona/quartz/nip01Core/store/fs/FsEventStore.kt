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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Filesystem-backed `IEventStore`. Each event is stored as a JSON file at
 * `events/<aa>/<bb>/<id>.json` (atomic tmp+rename) with `mtime` set to
 * `event.createdAt`. Hardlink indexes under `idx/` make filtered queries
 * cheap — see [FsIndexer] and [FsQueryPlanner].
 *
 * Step 2 scope: kind / author / owner / tag indexes; query planner with
 * post-filter via `FilterMatcher`; `count`. Later steps: replaceable /
 * addressable slots, NIP-09 tombstones, NIP-40 expiration, NIP-50 FTS,
 * NIP-62 vanish, transactions, scrub.
 */
class FsEventStore(
    val root: Path,
    indexingStrategy: IndexingStrategy = DefaultIndexingStrategy(),
) : IEventStore {
    private val layout = FsLayout(root)
    private val hasher: TagNameValueHasher
    private val indexer: FsIndexer
    private val planner: FsQueryPlanner

    init {
        layout.ensureSkeleton()
        cleanStaging()
        hasher = TagNameValueHasher(layout.readOrCreateSeed())
        indexer = FsIndexer(layout, hasher, indexingStrategy)
        planner = FsQueryPlanner(layout, hasher)
    }

    // ------------------------------------------------------------------
    // Insert
    // ------------------------------------------------------------------

    override fun insert(event: Event) {
        if (event.kind.isEphemeral()) return

        val canonical = layout.canonical(event.id)
        if (canonical.exists()) return

        Files.createDirectories(canonical.parent)
        val tmp = Files.createTempFile(layout.staging, event.id, FsLayout.JSON_EXT)
        try {
            Files.writeString(tmp, event.toJson())
            try {
                Files.move(tmp, canonical, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                // A concurrent writer won the race. Canonical is immutable
                // so the other copy is equivalent — drop our tmp and return.
                Files.deleteIfExists(tmp)
                return
            }
            Files.setLastModifiedTime(canonical, FileTime.from(event.createdAt, TimeUnit.SECONDS))
            indexer.link(event, canonical)
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
    // Query
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
        val limit = filter.limit ?: Int.MAX_VALUE
        if (limit <= 0) return
        var emitted = 0
        val seenIds = HashSet<HexKey>()
        for (candidate in planner.plan(filter)) {
            if (emitted >= limit) break
            if (!seenIds.add(candidate.id)) continue
            val event = readEvent(candidate.id) ?: continue
            if (!filter.match(event)) continue
            @Suppress("UNCHECKED_CAST")
            onEach(event as T)
            emitted++
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
        val ids = ArrayList<HexKey>()
        query<Event>(filter) { ids.add(it.id) }
        ids.forEach { delete(it) }
    }

    override fun delete(filters: List<Filter>) {
        val ids = HashSet<HexKey>()
        query<Event>(filters) { ids.add(it.id) }
        ids.forEach { delete(it) }
    }

    /** Delete an event by id. Returns 1 if a file was removed, 0 otherwise. */
    fun delete(id: HexKey): Int {
        val canonical = layout.canonical(id)
        val event = readEvent(id) // need tags to know which index links to remove
        if (event != null) indexer.unlink(event)
        return if (canonical.deleteIfExists()) 1 else 0
    }

    override fun deleteExpiredEvents() {
        // Step-5 feature.
    }

    override fun close() {
        // No long-lived resources yet.
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun readEvent(id: HexKey): Event? {
        val p = layout.canonical(id)
        if (!p.exists()) return null
        return try {
            Event.fromJson(p.readText())
        } catch (_: java.nio.file.NoSuchFileException) {
            null
        }
    }

    private fun cleanStaging() {
        Files.list(layout.staging).use { stream ->
            stream.forEach { Files.deleteIfExists(it) }
        }
    }
}
