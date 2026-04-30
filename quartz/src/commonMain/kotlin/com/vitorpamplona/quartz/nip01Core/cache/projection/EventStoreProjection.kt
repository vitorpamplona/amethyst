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
package com.vitorpamplona.quartz.nip01Core.cache.projection

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore.StoreChange
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.yield

/**
 * Lifecycle state of an [ObservableEventStore.project] flow.
 *
 *  - [Loading] is the initial state before the seed query completes.
 *    The UI should show a spinner / skeleton here.
 *  - [Loaded] holds the current deduped, sorted list of slots. Every
 *    membership change publishes a fresh [Loaded] instance; in-place
 *    addressable updates do *not* publish (the slots' own flows do).
 */
sealed interface ProjectionState<out T : Event> {
    data object Loading : ProjectionState<Nothing>

    data class Loaded<T : Event>(
        val items: List<MutableStateFlow<T>>,
    ) : ProjectionState<T>
}

/**
 * State machine that maintains a reactive view over an
 * [ObservableEventStore] for a fixed set of [filters]. Pure logic, no
 * coroutine ownership: construct, [seed] once, then [apply] each
 * [StoreChange] and read [snapshot]. For the standard "wrap into a
 * cold Flow and collect from a ViewModel" path, use
 * [ObservableEventStore.project] which composes this class.
 *
 * Each visible event is wrapped in a [MutableStateFlow], giving the
 * UI three change granularities:
 *
 *  - **Membership** (insert / removal): [snapshot] returns a fresh
 *    list. Stable list reference while membership is unchanged.
 *  - **In-place replaceable / addressable update**: same slot, new
 *    [MutableStateFlow.value]. Only collectors of that handle
 *    re-render. The slot's sort key is frozen at insertion so the
 *    list ordering doesn't reshuffle on a later `created_at`. Filter
 *    membership *is* re-evaluated against the new event, so a v2
 *    that no longer matches a filter (e.g. tag changed) drops out;
 *    a v2 that newly matches another filter joins it. In the common
 *    case (filter on `kinds + authors`) v2 still matches and the
 *    list reference stays stable.
 *  - **Removal**: NIP-09, NIP-62, NIP-40 expiration, `delete(filter)`.
 *
 * Limit is **per-filter**: each filter retains at most its own
 * `limit` matches; the snapshot is the deduped union, so disjoint
 * matches across filters can exceed any single cap. Removed slots
 * are not refilled from the store.
 *
 * Ephemeral events appear in the snapshot for as long as the
 * projection is alive; they never survive a re-seed (the inner store
 * never had them) and aren't touched by `deleteExpiredEvents()`.
 */
class EventStoreProjection<T : Event>(
    private val store: ObservableEventStore,
    private val filters: List<Filter>,
    private val nowProvider: () -> Long = TimeUtils::now,
) {
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

    /**
     * Run the seed query against the store and populate the indexes.
     * Call once, before the first [apply]. Expired events are skipped
     * (the store doesn't filter them at query time, so we do here).
     */
    suspend fun seed() {
        val now = nowProvider()
        for (event in store.query<T>(filters)) {
            if (event.isExpirationBefore(now)) continue
            applyInsert(event)
            yield()
        }
    }

    /**
     * Apply a [StoreChange] from [ObservableEventStore.changes].
     * Returns true if the change altered membership (caller should
     * publish a fresh [snapshot]); false for in-place addressable
     * updates, NIP-01 tiebreaker rejections, and arrivals that no
     * filter matches.
     */
    fun apply(storeEvent: StoreChange): Boolean =
        when (storeEvent) {
            is StoreChange.Insert -> {
                applyInsert(storeEvent.event)
            }

            is StoreChange.DeleteByFilter -> {
                dropWhere { ev -> storeEvent.filters.any { it.match(ev) } }
            }

            is StoreChange.DeleteExpired -> {
                // Store's sweep uses strict `<`; isExpirationBefore is `<=`, so subtract 1.
                val cutoff = (storeEvent.asOf ?: nowProvider()) - 1
                dropWhere { it.isExpirationBefore(cutoff) }
            }
        }

    /**
     * Current snapshot as a [ProjectionState.Loaded]. Cheap — the
     * deduped sorted union over [perFilter] sets is computed on
     * demand. Returns an empty [ProjectionState.Loaded] when no slots
     * are live.
     */
    fun snapshot(): ProjectionState.Loaded<T> {
        if (byId.isEmpty()) return ProjectionState.Loaded(emptyList())
        val union = sortedSetOf(slotComparator<T>())
        for (set in perFilter.values) union.addAll(set)
        return ProjectionState.Loaded(union.map { it.flow })
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
        if (event is RequestToVanishEvent && event.shouldVanishFrom(store.relay)) {
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

                // Same address, new winner. Rekey byId, mutate
                // flow.value in place, then re-evaluate filter
                // membership against the new event — v2 may add
                // matches or lose previously-matching filters.
                val previousId = existing.flow.value.id
                if (previousId != event.id) {
                    byId.remove(previousId)
                    byId[event.id] = existing
                }
                existing.flow.value = event as T

                var changed = false
                for ((f, set) in perFilter) {
                    val nowMatches = f.match(event)
                    val wasIn = set.contains(existing)
                    when {
                        nowMatches && !wasIn -> {
                            if (admit(existing, f, set)) changed = true
                        }

                        !nowMatches && wasIn -> {
                            set.remove(existing)
                            changed = true
                        }
                    }
                }
                // If no filter retains the slot anymore, fully drop it.
                if (perFilter.values.none { it.contains(existing) }) {
                    if (removeIndexes(existing)) changed = true
                }
                return changed
            }
        } else if (byId.containsKey(event.id)) {
            return false
        }

        // Genuinely new slot. Offer it to every matching filter; if
        // any filter still holds it after cap-eviction, the slot
        // becomes live and gets indexed.
        var changed = false
        val slot = Slot(event as T)
        for ((f, set) in perFilter) {
            if (!f.match(event)) continue
            if (admit(slot, f, set)) changed = true
        }
        if (perFilter.values.any { it.contains(slot) }) {
            byId[event.id] = slot
            if (address != null) byAddress[address] = slot
            changed = true
        }
        return changed
    }

    /**
     * Add [slot] to filter [f]'s [set] and evict the tail if the cap
     * is exceeded. Returns true if an eviction triggered a slot drop
     * from the indexes (its only filter retention was the evicted
     * one).
     */
    private fun admit(
        slot: Slot<T>,
        f: Filter,
        set: java.util.SortedSet<Slot<T>>,
    ): Boolean {
        set.add(slot)
        val cap = f.limit ?: return false
        var changed = false
        while (set.size > cap) {
            val tail = set.last()
            set.remove(tail)
            if (tail !== slot && perFilter.values.none { it.contains(tail) }) {
                if (removeIndexes(tail)) changed = true
            }
        }
        return changed
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

/**
 * Open a cold reactive projection over this observable store for the
 * given [filters]. The returned flow:
 *
 *  - emits [ProjectionState.Loading] on subscription;
 *  - runs the seed query against the store;
 *  - emits [ProjectionState.Loaded] with the seeded list;
 *  - then emits a fresh [ProjectionState.Loaded] every time membership
 *    changes (in-place addressable updates do not re-emit — collectors
 *    of the slot's own [MutableStateFlow] handle those).
 *
 * Each collection allocates its own state machine, runs its own seed,
 * and unsubscribes when the collector cancels — there's no
 * `close()`. For multiple collectors on the same view, share with
 * `stateIn(scope, SharingStarted.WhileSubscribed(...), Loading)` (the
 * standard ViewModel pattern).
 *
 * NIP-62 vanish handling is scoped by the inner store's `relay`.
 */
fun <T : Event> ObservableEventStore.project(filters: List<Filter>): Flow<ProjectionState<T>> =
    flow {
        val projection = EventStoreProjection<T>(this@project, filters)
        // Capture the outer collector so we can emit ProjectionState
        // from inside `changes.onSubscription { }` and `collect { }`,
        // where the implicit `this` is FlowCollector<StoreChange>.
        val outer = this
        emit(ProjectionState.Loading)
        // `onSubscription` runs after the SharedFlow subscription is
        // active but before we pull events — the buffer absorbs
        // emissions arriving during the seed query and we drain them
        // through `apply` once collect proceeds. Doing the seed
        // inside `collect { }` instead would race with concurrent
        // inserts.
        changes
            .onSubscription {
                projection.seed()
                outer.emit(projection.snapshot())
            }.collect { change ->
                if (projection.apply(change)) outer.emit(projection.snapshot())
            }
    }

fun <T : Event> ObservableEventStore.project(filter: Filter): Flow<ProjectionState<T>> = project(listOf(filter))
