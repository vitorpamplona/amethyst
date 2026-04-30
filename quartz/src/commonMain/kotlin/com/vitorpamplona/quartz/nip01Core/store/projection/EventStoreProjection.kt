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
package com.vitorpamplona.quartz.nip01Core.store.projection

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A reactive projection over an [ObservableEventStore] for a fixed
 * set of [filters]. Each visible event is wrapped in a
 * [MutableStateFlow] so the UI can collect three different kinds of
 * change with the right granularity:
 *
 *  - **Membership** (events arriving or leaving) re-emits a brand new
 *    [List] from [items]. The list reference is stable while membership
 *    is unchanged.
 *  - **In-place replaceable / addressable update** (a new version of
 *    the same `kind:pubkey:dtag` arrives) updates the existing handle's
 *    [MutableStateFlow.value] without touching the list. Only collectors
 *    of that one handle re-render. The list ordering is *not*
 *    reshuffled when the new version has a later `created_at` — each
 *    slot remembers the sort key it was inserted with, so updates feel
 *    like pure value mutations.
 *  - **Removal** (NIP-09 deletion, NIP-62 vanish, NIP-40 expiration,
 *    `delete(filter)`) drops the handle from the list.
 *
 * The seed is materialised by querying the store once at start, after
 * which the projection is driven entirely by
 * [ObservableEventStore.changes]. Three kinds of mutation arrive on
 * that stream:
 *
 *  - [StoreChange.Insert] — interpreted in-projection so a single
 *    arriving event can carry NIP-01 / NIP-09 / NIP-62 semantics:
 *      - **NIP-01 supersession.** New replaceable / addressable events
 *        replace prior ones for the same `kind:pubkey[:dtag]`. The
 *        NIP-01 lexical-id tiebreaker (`new.id < old.id` when
 *        `created_at` ties) is honoured.
 *      - **NIP-09 deletions.** A [DeletionEvent] removes any matching
 *        handle owned by the same author (for GiftWrap, the recipient).
 *        Cross-author deletions are inert.
 *      - **NIP-62 right to vanish.** A [RequestToVanishEvent] whose
 *        `shouldVanishFrom([relay])` is true drops every handle from
 *        the same author with `created_at < vanish.created_at`.
 *      - **NIP-40 expiration.** Events whose `expiration` tag has
 *        already lapsed at the moment they arrive are dropped before
 *        they ever enter [items].
 *
 *  - [StoreChange.DeleteByFilter] — emitted on `delete(filter)` /
 *    `delete(filters)`. The projection drops every slot matching any
 *    of the rule's filters via [Filter.match].
 *
 *  - [StoreChange.DeleteExpired] — emitted on `deleteExpiredEvents()`.
 *    The projection drops every slot whose `expiration` has lapsed at
 *    the cutoff the store pinned. **There is no per-projection
 *    expiration ticker** — projections only drop expired events when
 *    the application calls `deleteExpiredEvents()` on the store.
 *
 * Limit handling is **per-filter**: each filter retains at most its
 * own `limit` matches in a private capped set, sorted by created_at
 * DESC + id ASC. The projection's [items] is the deduped union of
 * those sets, so when filter A and filter B match disjoint events the
 * union can be larger than any single filter's `limit`. We do not
 * refill from the store after a deletion — if a removal leaves a
 * filter under cap, it stays under cap until another match arrives.
 *
 * Ephemeral events (kinds `20000-29999`) reach the projection via
 * [ObservableEventStore.changes] without ever being persisted; they
 * appear in [items] for as long as the projection is alive but never
 * survive a re-seed. They aren't covered by the store's
 * `deleteExpiredEvents()` sweep (the DB never had them), so an
 * ephemeral with an `expiration` tag will linger in the projection
 * until it's superseded or until the projection is closed.
 *
 * Lifecycle: the projection runs a single collector inside [scope].
 * Cancel the scope (or call [close]) when the screen using the
 * projection goes away.
 */
class EventStoreProjection<T : Event>(
    private val store: ObservableEventStore,
    private val filters: List<Filter>,
    private val relay: NormalizedRelayUrl?,
    scope: CoroutineScope,
    private val nowProvider: () -> Long = TimeUtils::now,
) : AutoCloseable {
    private val _items = MutableStateFlow<List<MutableStateFlow<T>>>(emptyList())
    val items: StateFlow<List<MutableStateFlow<T>>> = _items.asStateFlow()

    /** Slots keyed by the *current* event id. Re-keyed when a replaceable / addressable handle takes a new version. */
    private val byId = HashMap<HexKey, Slot<T>>()

    /** Slots keyed by replaceable / addressable [Address] for in-place updates and supersession lookups. */
    private val byAddress = HashMap<Address, Slot<T>>()

    /**
     * Per-filter capped sets. Each filter independently retains at most
     * `filter.limit` matches; a slot is "live" iff it appears in at
     * least one of these sets. Identity-keyed — [Filter] is `@Stable`
     * but not a data class.
     */
    private val perFilter: Map<Filter, java.util.SortedSet<Slot<T>>> =
        filters.associateWith { sortedSetOf(slotComparator()) }

    /** Set when the seed has been written to [items], so callers can suspend until the projection is hot. */
    val ready: CompletableDeferred<Unit> = CompletableDeferred()

    private val collectorJob: Job =
        scope.launch {
            // `onSubscription` runs after the SharedFlow subscription is
            // active but before we pull any events — the buffer absorbs
            // emissions arriving during seed and drains them once collect
            // proceeds. Doing the seed inside `collect { }` would race
            // with concurrent inserts.
            store.changes
                .onSubscription {
                    seed()
                    ready.complete(Unit)
                }.collect { storeEvent -> apply(storeEvent) }
        }

    private suspend fun seed() {
        val now = nowProvider()
        for (event in store.query<T>(filters)) {
            // Stores don't filter expired rows at query time, so do it
            // here — otherwise an expired event would briefly appear
            // in [items] before the next deleteExpiredEvents() sweep.
            if (event.isExpirationBefore(now)) continue
            applyInsert(event)
            yield()
        }
        publish()
    }

    private fun apply(storeEvent: StoreChange) {
        val changed =
            when (storeEvent) {
                is StoreChange.Insert -> {
                    applyInsert(storeEvent.event)
                }

                is StoreChange.DeleteByFilter -> {
                    dropWhere { ev -> storeEvent.filters.any { it.match(ev) } }
                }

                // Store's sweep uses strict `<`; isExpirationBefore is
                // `<=`, so subtract 1 to match.
                is StoreChange.DeleteExpired -> {
                    val cutoff = (storeEvent.asOf ?: nowProvider()) - 1
                    dropWhere { it.isExpirationBefore(cutoff) }
                }
            }
        if (changed) publish()
    }

    private fun applyInsert(event: Event): Boolean {
        if (event.isExpirationBefore(nowProvider())) return false

        var changed = false

        // NIP-09 / NIP-62 side effects come first — a deletion event
        // that arrives at the same instant as a matching event still
        // removes its targets.
        if (event is DeletionEvent) {
            if (handleDeletion(event)) changed = true
        }
        if (event is RequestToVanishEvent && event.shouldVanishFrom(relay)) {
            if (dropWhere { ev -> ownerOf(ev) == event.pubKey && ev.createdAt < event.createdAt }) changed = true
        }

        if (handleInsert(event)) changed = true

        return changed
    }

    /**
     * Returns true if processing the event caused the projection's
     * membership to change. Returns false for in-place supersession
     * updates, NIP-01 tiebreaker rejections, and arrivals that no
     * filter matches.
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleInsert(event: Event): Boolean {
        val address = addressOf(event)

        if (address != null) {
            val existing = byAddress[address]
            if (existing != null) {
                if (!supersedes(event, existing.flow.value)) return false

                // Same address, new winner. Rekey byId from the
                // previous event id to the new one and update the
                // handle's value in place — list reference stays the
                // same; only the handle's collectors re-render.
                val previousId = existing.flow.value.id
                if (previousId != event.id) {
                    byId.remove(previousId)
                    byId[event.id] = existing
                }
                existing.flow.value = event as T
                return false
            }
        } else if (byId.containsKey(event.id)) {
            return false
        }

        // Genuinely new slot. Offer it to every matching filter; if
        // any filter still holds it after cap-eviction, the slot
        // becomes live and gets indexed.
        var membershipChanged = false
        val slot = Slot(event as T)
        for ((f, set) in perFilter) {
            if (!f.match(event)) continue
            set.add(slot)
            val cap = f.limit ?: continue
            while (set.size > cap) {
                val tail = set.last()
                set.remove(tail)
                if (tail !== slot && perFilter.values.none { it.contains(tail) }) {
                    // Tail no longer retained by any filter — fully drop.
                    if (removeIndexes(tail)) membershipChanged = true
                }
            }
        }

        // The slot survived cap-eviction in at least one filter, so it
        // belongs in the indexes. Otherwise nothing was indexed and
        // the only membership effect is whatever evictions happened
        // along the way.
        if (perFilter.values.any { it.contains(slot) }) {
            byId[event.id] = slot
            if (address != null) byAddress[address] = slot
            membershipChanged = true
        }
        return membershipChanged
    }

    private fun handleDeletion(deletion: DeletionEvent): Boolean {
        var changed = false

        // NIP-09 by id, only if the deletion's author owns the target.
        // For GiftWrap, the owner is the p-tag recipient.
        for (id in deletion.deleteEventIds()) {
            val slot = byId[id] ?: continue
            if (ownerOf(slot.flow.value) == deletion.pubKey && removeSlot(slot)) changed = true
        }

        // NIP-09 by address, only original author, only events with
        // `created_at <= deletion.created_at`. `addr` is already an
        // [Address] — same equals/hashCode as our index keys.
        for (addr in deletion.deleteAddresses()) {
            if (addr.pubKeyHex != deletion.pubKey) continue
            val slot = byAddress[addr] ?: continue
            if (slot.flow.value.createdAt <= deletion.createdAt && removeSlot(slot)) changed = true
        }

        return changed
    }

    /** Drop every live slot whose current event matches [predicate]. */
    private inline fun dropWhere(predicate: (Event) -> Boolean): Boolean {
        // Snapshot before mutating — removeSlot mutates byId.
        val targets = byId.values.filter { predicate(it.flow.value) }
        var changed = false
        for (slot in targets) {
            if (removeSlot(slot)) changed = true
        }
        return changed
    }

    /** Remove a live slot from every index AND from each per-filter set. */
    private fun removeSlot(slot: Slot<T>): Boolean {
        for (set in perFilter.values) set.remove(slot)
        return removeIndexes(slot)
    }

    /**
     * Remove a slot from [byId] / [byAddress] without touching the
     * per-filter sets. Used by the per-filter eviction loop, which
     * already owns the bookkeeping for those.
     */
    private fun removeIndexes(slot: Slot<T>): Boolean {
        val removed = byId.remove(slot.flow.value.id) != null
        if (!removed) return false
        addressOf(slot.flow.value)?.let(byAddress::remove)
        return true
    }

    private fun publish() {
        // Lazily compute the deduped sorted union from per-filter
        // sets. Cheaper than maintaining a separate `ordered` field
        // alongside every insert / remove.
        if (byId.isEmpty()) {
            _items.value = emptyList()
            return
        }
        val union = sortedSetOf(slotComparator<T>())
        for (set in perFilter.values) union.addAll(set)
        _items.value = union.map { it.flow }
    }

    /**
     * Stop tracking changes and clear internal state. Idempotent. The
     * scope passed to the constructor keeps running; only this
     * projection's collector job is cancelled.
     */
    override fun close() {
        collectorJob.cancel()
        byId.clear()
        byAddress.clear()
        for (set in perFilter.values) set.clear()
        _items.value = emptyList()
    }

    /**
     * Internal slot. Each event added to the projection lives inside
     * one of these for as long as it survives. The sort key is frozen
     * at construction time — supersession in-place updates rewrite
     * `flow.value` but never the sort key, so the position inside
     * each [perFilter] set is stable across updates.
     */
    private class Slot<T : Event>(
        initial: T,
    ) {
        val sortCreatedAt: Long = initial.createdAt
        val sortId: HexKey = initial.id
        val flow: MutableStateFlow<T> = MutableStateFlow(initial)
    }

    companion object {
        /** [Address] for replaceable / addressable supersession; `null` for regular events. */
        private fun addressOf(event: Event): Address? =
            when {
                event is AddressableEvent -> event.address()
                event.kind.isReplaceable() -> Address(event.kind, event.pubKey, "")
                else -> null
            }

        /**
         * NIP-01 supersession tiebreaker. The new event wins iff its
         * `created_at` is strictly greater, or the timestamps tie and
         * its `id` is lexically smaller.
         */
        private fun supersedes(
            new: Event,
            existing: Event,
        ): Boolean =
            when {
                new.createdAt > existing.createdAt -> true
                new.createdAt < existing.createdAt -> false
                else -> new.id < existing.id
            }

        /**
         * Owner pubkey for ownership checks (NIP-09 author match,
         * NIP-62 vanish target). For GiftWrap the owner is the p-tag
         * recipient; for everything else it's `event.pubKey`.
         */
        private fun ownerOf(event: Event): HexKey = (event as? GiftWrapEvent)?.recipientPubKey() ?: event.pubKey

        /**
         * created_at DESC, id ASC. The keys are snapshots taken at
         * insertion time, so a slot's position never changes after it
         * joins a set. Distinct events have distinct ids, so no third
         * tiebreak is needed.
         */
        private fun <T : Event> slotComparator(): Comparator<Slot<T>> =
            Comparator { a, b ->
                if (a === b) return@Comparator 0
                val byTime = b.sortCreatedAt.compareTo(a.sortCreatedAt)
                if (byTime != 0) return@Comparator byTime
                a.sortId.compareTo(b.sortId)
            }
    }
}
