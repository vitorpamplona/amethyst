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

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
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
    private val slots: FsSlots
    private val tombstones: FsTombstones
    private val planner: FsQueryPlanner

    init {
        layout.ensureSkeleton()
        cleanStaging()
        hasher = TagNameValueHasher(layout.readOrCreateSeed())
        indexer = FsIndexer(layout, hasher, indexingStrategy)
        slots = FsSlots(layout, indexer)
        tombstones = FsTombstones(layout)
        planner = FsQueryPlanner(layout, hasher)
    }

    // ------------------------------------------------------------------
    // Insert
    // ------------------------------------------------------------------

    override fun insert(event: Event) {
        if (event.kind.isEphemeral()) return
        if (isBlockedByTombstone(event)) return

        val slot = slots.slotPathFor(event)
        val existingSlot = slot?.let { slots.readSlot(it) }
        if (existingSlot != null && existingSlot.createdAt >= event.createdAt) {
            // Newer or equal-timestamp version already owns this slot.
            // Matches ReplaceableModule / AddressableModule blocking
            // behaviour in SQLite.
            return
        }

        val canonical = layout.canonical(event.id)
        if (canonical.exists()) {
            // Same id already written. If this event is a replaceable /
            // addressable whose slot points somewhere else, still install
            // the slot so the winner is consistent.
            if (slot != null && existingSlot?.id != event.id) {
                slots.install(slot, canonical, event, existingSlot)
            }
            if (event is DeletionEvent) processDeletion(event, canonical)
            return
        }

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
            if (slot != null) {
                slots.install(slot, canonical, event, existingSlot)
            }
            if (event is DeletionEvent) processDeletion(event, canonical)
        } catch (t: Throwable) {
            Files.deleteIfExists(tmp)
            throw t
        }
    }

    /**
     * NIP-09 pre-insert guard. Parity with SQLite's `reject_deleted_events`
     * BEFORE INSERT trigger: id-scoped tombstones always block; address-
     * scoped tombstones block when the existing cutoff is `>=` the new
     * event's createdAt.
     */
    private fun isBlockedByTombstone(event: Event): Boolean {
        if (tombstones.hasIdTombstone(event.id)) return true
        if (event is AddressableEvent && event.kind.isAddressable()) {
            val cutoff = tombstones.addrTombstoneCutoff(event.kind, event.pubKey, event.dTag())
            if (cutoff != null && event.createdAt <= cutoff) return true
        }
        if (event.kind.isReplaceable()) {
            val cutoff = tombstones.addrTombstoneCutoff(event.kind, event.pubKey, "")
            if (cutoff != null && event.createdAt <= cutoff) return true
        }
        return false
    }

    /**
     * Applies a kind-5 deletion's side effects: cascade-deletes each
     * target owned by the deletion author (matching SQLite's `pubkey_
     * owner_hash` check, which covers GiftWrap recipients), clears any
     * slot the targets owned, and installs tombstone hardlinks so
     * future re-inserts are blocked.
     */
    private fun processDeletion(
        deletion: DeletionEvent,
        deletionCanonical: java.nio.file.Path,
    ) {
        val ownerHashOfDeletion = hasher.hash(deletion.pubKey)

        for (targetId in deletion.deleteEventIds()) {
            val targetEvent = readEvent(targetId)
            if (targetEvent != null && indexer.ownerHash(targetEvent) == ownerHashOfDeletion) {
                indexer.unlink(targetEvent)
                val targetSlot = slots.slotPathFor(targetEvent)
                if (targetSlot != null && slots.readSlot(targetSlot)?.id == targetId) {
                    slots.clear(targetSlot)
                }
                layout.canonical(targetId).deleteIfExists()
            }
            tombstones.installId(targetId, deletionCanonical)
        }

        for (addr in deletion.deleteAddresses()) {
            // Cascade is only honoured when the address's author matches
            // the deletion author — matching SQLite's WHERE pubkey = ?.
            if (addr.pubKeyHex == deletion.pubKey) {
                val slot =
                    when {
                        addr.kind.isReplaceable() -> layout.replaceableSlot(addr.kind, addr.pubKeyHex)
                        addr.kind.isAddressable() -> layout.addressableSlot(addr.kind, addr.pubKeyHex, addr.dTag)
                        else -> null
                    }
                if (slot != null) {
                    val winner = slots.readSlot(slot)
                    if (winner != null && winner.createdAt <= deletion.createdAt) {
                        indexer.unlink(winner)
                        layout.canonical(winner.id).deleteIfExists()
                        slots.clear(slot)
                    }
                }
            }
            tombstones.installAddr(addr.kind, addr.pubKeyHex, addr.dTag, deletion, deletionCanonical)
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
        if (event != null) {
            indexer.unlink(event)
            val slot = slots.slotPathFor(event)
            if (slot != null) {
                val winner = slots.readSlot(slot)
                if (winner?.id == id) slots.clear(slot)
            }
        }
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
