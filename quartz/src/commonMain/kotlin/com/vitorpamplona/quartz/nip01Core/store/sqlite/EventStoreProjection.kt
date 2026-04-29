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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A reactive projection over the [SQLiteEventStore] for a fixed set of
 * [filters]. Each visible event is wrapped in a [MutableStateFlow] so
 * the UI can collect three different kinds of change with the right
 * granularity:
 *
 *  - **Membership** (events arriving or leaving) re-emits a brand new
 *    [List] from [items]. The list reference is stable while membership
 *    is unchanged.
 *  - **In-place addressable update** (a new version of the same
 *    `kind:pubkey:dtag` arrives) updates the existing handle's
 *    [MutableStateFlow.value] without touching the list. Only collectors
 *    of that one handle re-render. The list ordering is *not* reshuffled
 *    when the new version has a later `created_at` — each slot remembers
 *    the sort key it was inserted with, so addressable updates feel like
 *    pure value mutations.
 *  - **Removal** (NIP-09 deletion, NIP-62 vanish, NIP-40 expiration, or
 *    a non-addressable event being explicitly deleted) drops the handle
 *    from the list.
 *
 * The seed is materialised by running the filters against the store
 * once at start. After that, [items] is driven entirely by
 * [SQLiteEventStore.changes]; the database is not re-queried on every
 * mutation.
 *
 * Limit handling matches the existing in-memory observables in
 * `commons/observables`: the initial query honours the filter `limit`,
 * and we trim the list to the same cap when an insert pushes it over.
 * We do **not** refill from the DB after a deletion — if a deletion
 * leaves you under the limit, you stay under the limit until something
 * new arrives. That tradeoff keeps the projection allocation-free per
 * mutation and matches what callers were already getting from
 * `LocalCache.observeEvents`.
 *
 * Lifecycle: the projection runs a single coroutine in [scope]. Cancel
 * the scope (or call [close]) when the screen using the projection
 * goes away. There is no shared state between projections; each one
 * keeps its own indexes.
 */
class EventStoreProjection<T : Event>(
    private val store: SQLiteEventStore,
    private val filters: List<Filter>,
    scope: CoroutineScope,
) : AutoCloseable {
    private val _items = MutableStateFlow<List<MutableStateFlow<T>>>(emptyList())
    val items: StateFlow<List<MutableStateFlow<T>>> = _items.asStateFlow()

    /** Slots keyed by the *current* event id. Re-keyed when an addressable handle takes a new version. */
    private val byId = HashMap<HexKey, Slot<T>>()

    /** Slots keyed by `kind:pubkey:dtag` for in-place addressable updates. */
    private val byAddress = HashMap<String, Slot<T>>()

    /**
     * Sorted view of the same slots. The comparator uses each slot's
     * frozen sort key (the seed event's `created_at` + `id`), so an
     * addressable update never moves a slot inside this set.
     */
    private val ordered = sortedSetOf(slotComparator<T>())

    private val limit: Int? = filters.mapNotNull { it.limit }.maxOrNull()

    /** Set when the seed has been written to [items], so callers can suspend until the projection is hot. */
    val ready: CompletableDeferred<Unit> = CompletableDeferred()

    private val job: Job =
        scope.launch {
            seed()
            ready.complete(Unit)
            store.changes.collect { change -> apply(change) }
        }

    private suspend fun seed() {
        val initial = store.query<T>(filters)
        for (event in initial) {
            insertNew(event)
            // Cooperate with cancellation on very large seeds.
            yield()
        }
        publish()
    }

    private fun apply(change: StoreChange) {
        var changed = false

        // Removals first. A replaceable / addressable supersession
        // arrives as `inserted = [new]` plus `removedIds = [oldId]`.
        // When the new event is addressable and an existing slot
        // already maps that address, [handleInsert] will rekey
        // `byId` from the old id to the new id before this loop sees
        // the old id — so the lookup here is a no-op for that case
        // and the slot stays in place. For non-addressable
        // replaceables (kinds 0/3/10000-19999) the old event has a
        // different id, no address index, and we genuinely drop it.
        for (event in change.inserted) {
            if (handleInsert(event)) changed = true
        }
        for (id in change.removedIds) {
            if (handleRemove(id)) changed = true
        }

        if (changed) publish()
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleInsert(event: Event): Boolean {
        if (filters.none { it.match(event) }) return false

        if (event is AddressableEvent) {
            val key = event.addressTag()
            val existing = byAddress[key]
            if (existing != null) {
                // Same address, new version. Rekey byId from the
                // previous event id to the new one and update the
                // handle's value in place — list reference does not
                // change, only the handle's collectors re-render.
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

        insertNew(event)
        return true
    }

    private fun handleRemove(id: HexKey): Boolean {
        val slot = byId.remove(id) ?: return false
        ordered.remove(slot)
        val ev = slot.flow.value
        if (ev is AddressableEvent) {
            val addr = ev.addressTag()
            // Only clear the address index if this slot still owns it.
            if (byAddress[addr] === slot) byAddress.remove(addr)
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun insertNew(event: Event) {
        val slot = Slot(event as T)
        byId[event.id] = slot
        if (event is AddressableEvent) byAddress[event.addressTag()] = slot
        ordered.add(slot)

        val cap = limit ?: return
        while (ordered.size > cap) {
            val tail = ordered.last()
            ordered.remove(tail)
            val tailEvent = tail.flow.value
            byId.remove(tailEvent.id)
            if (tailEvent is AddressableEvent) {
                val addr = tailEvent.addressTag()
                if (byAddress[addr] === tail) byAddress.remove(addr)
            }
        }
    }

    private fun publish() {
        _items.value = ordered.map { it.flow }
    }

    /**
     * Stop tracking changes and clear internal state. Idempotent. The
     * scope passed to the constructor keeps running; only this
     * projection's collector job is cancelled.
     */
    override fun close() {
        job.cancel()
        ordered.clear()
        byId.clear()
        byAddress.clear()
        _items.value = emptyList()
    }

    /**
     * Internal slot. Each event added to the projection lives inside
     * one of these for as long as it survives. The sort key is frozen
     * at construction time — addressable in-place updates rewrite
     * `flow.value` but never the sort key, so the ordering inside
     * [ordered] is stable across updates.
     */
    private class Slot<T : Event>(
        initial: T,
    ) {
        val sortCreatedAt: Long = initial.createdAt
        val sortId: HexKey = initial.id
        val flow: MutableStateFlow<T> = MutableStateFlow(initial)
    }

    companion object {
        /**
         * created_at DESC, id ASC. The keys are snapshots taken at
         * insertion time, so the ordering of a slot never changes
         * after it joins the set. Distinct events have distinct ids,
         * so no third-key tiebreak is needed.
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
