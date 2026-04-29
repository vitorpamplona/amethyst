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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
open class FsEventStore(
    val root: Path,
    indexingStrategy: IndexingStrategy = DefaultIndexingStrategy(),
    /**
     * Optional relay URL the store is acting on behalf of. Used by
     * NIP-62 [RequestToVanishEvent.shouldVanishFrom] scoping. When
     * `null`, only "ALL_RELAYS" vanish requests cascade — matches
     * `SQLiteEventStore`'s relay arg semantics.
     */
    private val relay: NormalizedRelayUrl? = null,
    /**
     * How to render an event to JSON before writing the canonical file.
     * Default is the compact NIP-01 form ([Event.toJson]); CLIs that
     * surface store files to humans pass a pretty-printer instead. The
     * stored bytes are not re-used for signature checks anyway —
     * verification re-canonicalises — so format is purely a UX choice.
     */
    private val eventToJson: (Event) -> String = Event::toJson,
) : IEventStore {
    private val layout = FsLayout(root)
    private val hasher: TagNameValueHasher
    private val indexer: FsIndexer
    private val slots: FsSlots
    private val tombstones: FsTombstones
    private val planner: FsQueryPlanner
    private val lockManager: FsLockManager

    init {
        layout.ensureSkeleton()
        cleanStaging()
        lockManager = FsLockManager(root)
        hasher = TagNameValueHasher(layout.readOrCreateSeed())
        indexer = FsIndexer(layout, hasher, indexingStrategy)
        slots = FsSlots(layout, indexer)
        tombstones = FsTombstones(layout)
        planner = FsQueryPlanner(layout, hasher)
    }

    /**
     * Stream of events newly persisted by this store. See
     * [IEventStore.inserts] for the contract. Events that are no-op
     * idempotent retries (canonical already on disk from a previous
     * call) are not re-emitted; the original insert already published.
     */
    private val _inserts =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )
    override val inserts: SharedFlow<Event> = _inserts.asSharedFlow()

    // ------------------------------------------------------------------
    // Insert
    // ------------------------------------------------------------------

    override suspend fun insert(event: Event) {
        val accepted = lockManager.withWriteLock { insertLocked(event) }
        if (accepted) _inserts.emit(event)
    }

    /**
     * Inserts the event under the write lock and returns true iff this
     * call is the one that newly persisted it. Returns false for the
     * no-op paths (ephemeral, expired, blocked, supersession loser, or
     * canonical already on disk from a prior call) so callers can
     * decide whether to publish on [inserts].
     */
    private fun insertLocked(event: Event): Boolean {
        if (event.kind.isEphemeral()) return false
        if (isAlreadyExpired(event)) return false
        if (isBlockedByTombstone(event)) return false

        val slot = slots.slotPathFor(event)
        val existingSlot = slot?.let { slots.readSlot(it) }
        if (existingSlot != null &&
            (
                existingSlot.createdAt > event.createdAt ||
                    (existingSlot.createdAt == event.createdAt && existingSlot.id <= event.id)
            )
        ) {
            // Existing slot winner outranks the incoming event under NIP-01
            // (later createdAt, or same createdAt with the lexically smaller
            // id). Matches the ReplaceableModule / AddressableModule trigger
            // condition in SQLite.
            return false
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
            if (event is RequestToVanishEvent) processVanish(event, canonical)
            return false
        }

        Files.createDirectories(canonical.parent)
        val tmp = Files.createTempFile(layout.staging, event.id, FsLayout.JSON_EXT)
        try {
            Files.writeString(tmp, eventToJson(event))
            try {
                Files.move(tmp, canonical, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                // A concurrent writer won the race. Canonical is immutable
                // so the other copy is equivalent — drop our tmp and return.
                Files.deleteIfExists(tmp)
                return false
            }
            Files.setLastModifiedTime(canonical, FileTime.from(event.createdAt, TimeUnit.SECONDS))
            indexer.link(event, canonical)
            if (slot != null) {
                slots.install(slot, canonical, event, existingSlot)
            }
            if (event is DeletionEvent) processDeletion(event, canonical)
            if (event is RequestToVanishEvent) processVanish(event, canonical)
            return true
        } catch (t: Throwable) {
            Files.deleteIfExists(tmp)
            throw t
        }
    }

    /**
     * Source of "now" in unix seconds. Defaults to [TimeUtils.now]; tests
     * override this via a subclass so NIP-40 expiration behaviour (insert
     * guard and periodic sweep) can be driven deterministically without
     * waiting on the wall clock or patching a global time source.
     */
    protected open fun now(): Long = TimeUtils.now()

    /**
     * NIP-40 pre-insert guard. Parity with SQLite's `reject_expired_events`
     * trigger: an event whose expiration tag is `<= now` is rejected
     * outright. The `<= 0` clause matches the SQLite check that ignores
     * non-positive expiration values.
     */
    private fun isAlreadyExpired(event: Event): Boolean {
        val exp = event.expiration() ?: return false
        if (exp <= 0) return false
        return exp <= now()
    }

    /**
     * NIP-09 pre-insert guard. Parity with SQLite's `reject_deleted_events`
     * BEFORE INSERT trigger: id and addr tombstones only block when the
     * deletion's author matches the candidate event's owner pubkey —
     * SQLite checks `event_tags.pubkey_hash = NEW.pubkey_owner_hash`. For
     * GiftWraps the owner is the recipient (p-tag), matching `pubkey_
     * owner_hash`. Addr tombstones additionally enforce the
     * `event.createdAt <= cutoff` window.
     */
    private fun isBlockedByTombstone(event: Event): Boolean {
        val ownerPubKey = indexer.ownerPubKey(event)
        val tombstoneAuthor = tombstones.idTombstoneOwnerPubKey(event.id)
        if (tombstoneAuthor != null && tombstoneAuthor == ownerPubKey) return true
        if (event is AddressableEvent && event.kind.isAddressable()) {
            val cutoff = tombstones.addrTombstoneCutoff(event.kind, event.pubKey, event.dTag())
            if (cutoff != null && event.createdAt <= cutoff) return true
        }
        if (event.kind.isReplaceable()) {
            val cutoff = tombstones.addrTombstoneCutoff(event.kind, event.pubKey, "")
            if (cutoff != null && event.createdAt <= cutoff) return true
        }
        // NIP-62: any event whose owner has an active vanish request with
        // createdAt >= event.createdAt is rejected. Owner is the recipient
        // for GiftWrap, matching SQLite's `pubkey_owner_hash`.
        val vanishCutoff = tombstones.vanishCutoff(indexer.ownerHash(event))
        if (vanishCutoff != null && event.createdAt <= vanishCutoff) return true
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
            // Per NIP-09 only the original author can delete their own
            // addressable. If the deletion's author doesn't own the
            // address, ignore both the cascade AND the tombstone install
            // — otherwise a stranger's stray `a`-tag would block the
            // legitimate owner from re-publishing.
            if (addr.pubKeyHex != deletion.pubKey) continue

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
            tombstones.installAddr(addr.kind, addr.pubKeyHex, addr.dTag, deletion, deletionCanonical)
        }
    }

    override suspend fun transaction(body: IEventStore.ITransaction.() -> Unit) {
        val accepted = ArrayList<Event>()
        lockManager.withWriteLock {
            val txn =
                object : IEventStore.ITransaction {
                    override fun insert(event: Event) {
                        if (insertLocked(event)) accepted.add(event)
                    }
                }
            txn.body()
        }
        // Emit each accepted event in order after the lock releases —
        // mirrors SQLiteEventStore.transaction.
        for (e in accepted) _inserts.emit(e)
    }

    // ------------------------------------------------------------------
    // Query
    // ------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Event> query(filter: Filter): List<T> {
        val out = mutableListOf<T>()
        query<T>(filter) { out.add(it) }
        return out
    }

    override suspend fun <T : Event> query(filters: List<Filter>): List<T> {
        val seen = HashSet<HexKey>()
        val out = mutableListOf<T>()
        filters.forEach { f ->
            query<T>(f) { if (seen.add(it.id)) out.add(it) }
        }
        return out
    }

    override suspend fun <T : Event> query(
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

    override suspend fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) {
        val seen = HashSet<HexKey>()
        filters.forEach { f ->
            query<T>(f) { if (seen.add(it.id)) onEach(it) }
        }
    }

    override suspend fun count(filter: Filter): Int {
        var n = 0
        query<Event>(filter) { n++ }
        return n
    }

    override suspend fun count(filters: List<Filter>): Int {
        var n = 0
        query<Event>(filters) { n++ }
        return n
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /**
     * Safe-by-default: an empty filter (or a list of only empty filters)
     * deletes nothing, so a stray `delete(Filter())` cannot wipe the
     * entire store. This is asymmetric with `query(Filter())` which
     * intentionally returns every event — same contract as `SQLiteEventStore`.
     */
    override suspend fun delete(filter: Filter) =
        lockManager.withWriteLock {
            if (filter.isEmpty()) return@withWriteLock
            val ids = ArrayList<HexKey>()
            query<Event>(filter) { ids.add(it.id) }
            ids.forEach { deleteLocked(it) }
        }

    /** See [delete] for the empty-filter contract. */
    override suspend fun delete(filters: List<Filter>) =
        lockManager.withWriteLock {
            val nonEmpty = filters.filterNot { it.isEmpty() }
            if (nonEmpty.isEmpty()) return@withWriteLock
            val ids = HashSet<HexKey>()
            query<Event>(nonEmpty) { ids.add(it.id) }
            ids.forEach { deleteLocked(it) }
        }

    /** Delete an event by id. Returns 1 if a file was removed, 0 otherwise. */
    suspend fun delete(id: HexKey): Int =
        lockManager.withWriteLock {
            deleteLocked(id)
        }

    private fun deleteLocked(id: HexKey): Int {
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

    /**
     * NIP-62 right-to-vanish processing. When the kind-62 event scopes
     * to this store's relay (or "ALL_RELAYS"), install/replace the
     * vanish tombstone keyed by owner hash and cascade-delete every
     * event from that owner with `createdAt < vanish.createdAt`. Mirrors
     * SQLite's `delete_events_on_event_vanish` AFTER-INSERT trigger.
     */
    private fun processVanish(
        event: RequestToVanishEvent,
        canonical: java.nio.file.Path,
    ) {
        if (!event.shouldVanishFrom(relay)) return
        val ownerHash = hasher.hash(event.pubKey)
        if (!tombstones.installVanish(ownerHash, event, canonical)) return

        // Cascade: walk idx/owner/<ownerHex>/, delete every event whose
        // ts < vanish.createdAt. The vanish event's own ts equals
        // vanish.createdAt so it survives.
        val ownerDir = layout.idxOwner.resolve(FsLayout.hashHex(ownerHash))
        if (!Files.isDirectory(ownerDir)) return
        val toDelete = ArrayList<HexKey>()
        Files.list(ownerDir).use { stream ->
            for (entry in stream) {
                val parsed = FsLayout.parseEntry(entry.fileName.toString()) ?: continue
                if (parsed.first < event.createdAt) toDelete.add(parsed.second)
            }
        }
        // Already inside the writer lock (insertLocked → processVanish);
        // call the locked variant to avoid trying to re-suspend on the
        // public `delete(id)` from a non-suspend body.
        toDelete.forEach { deleteLocked(it) }
    }

    /**
     * Sweep expired events. Walks `idx/expires_at/`, parses `<exp>-<id>`
     * filenames, and deletes any entry whose `exp < now`. Matches SQLite's
     * `expiration < unixepoch()` predicate (note: strict `<`, not `<=`).
     */
    override suspend fun deleteExpiredEvents() =
        lockManager.withWriteLock {
            if (!Files.isDirectory(layout.idxExpiresAt)) return@withWriteLock
            val now = now()
            val toDelete = ArrayList<HexKey>()
            Files.list(layout.idxExpiresAt).use { stream ->
                for (entry in stream) {
                    val parsed = FsLayout.parseEntry(entry.fileName.toString()) ?: continue
                    if (parsed.first < now) toDelete.add(parsed.second)
                }
            }
            toDelete.forEach { deleteLocked(it) }
        }

    override fun close() {
        lockManager.close()
    }

    // ------------------------------------------------------------------
    // Maintenance
    // ------------------------------------------------------------------

    /**
     * Rebuild every `idx/` entry from the canonical events. Safe to run
     * after external edits or to recover from a partial-write crash.
     * Tombstones, replaceable / addressable slots, and the seed file are
     * left untouched — slot-only-pinned events stay reachable, and
     * tombstone removal is treated as a deliberate "un-forget" by the
     * user.
     */
    fun scrub() =
        lockManager.withWriteLock {
            cleanStaging()
            // Wipe and recreate idx/.
            deleteRecursively(layout.idx)
            layout.ensureSkeleton()

            if (!Files.isDirectory(layout.events)) return@withWriteLock
            Files.walk(layout.events).use { stream ->
                for (path in stream) {
                    if (!Files.isRegularFile(path)) continue
                    if (!path.fileName.toString().endsWith(FsLayout.JSON_EXT)) continue
                    val event =
                        try {
                            Event.fromJson(Files.readString(path))
                        } catch (_: java.io.IOException) {
                            continue
                        } catch (_: com.fasterxml.jackson.core.JacksonException) {
                            continue
                        }
                    indexer.link(event, path)
                }
            }
        }

    /**
     * Drop dangling `idx/` entries whose canonical no longer exists.
     * Cheaper than [scrub] because it never opens an event JSON; it just
     * stats the canonical path encoded in the index entry filename.
     */
    fun compact() =
        lockManager.withWriteLock {
            if (!Files.isDirectory(layout.idx)) return@withWriteLock
            Files.walk(layout.idx).use { stream ->
                for (path in stream) {
                    if (!Files.isRegularFile(path)) continue
                    val parsed = FsLayout.parseEntry(path.fileName.toString()) ?: continue
                    if (!layout.canonical(parsed.second).exists()) {
                        Files.deleteIfExists(path)
                    }
                }
            }
        }

    private fun deleteRecursively(p: java.nio.file.Path) {
        if (!Files.exists(p)) return
        Files.walk(p).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
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
