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
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.observable.DeleteRule
import com.vitorpamplona.quartz.nip01Core.store.observable.ObservableEventStore
import com.vitorpamplona.quartz.nip01Core.store.observable.StoreEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
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
 * [MutableStateFlow] so
 * the UI can collect three different kinds of change with the right
 * granularity:
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
 *  - **Removal** (NIP-09 deletion, NIP-62 vanish, NIP-40 expiration)
 *    drops the handle from the list.
 *
 * The seed is materialised by querying the store once at start,
 * after which the projection is driven entirely by
 * [ObservableEventStore.events]. Two kinds of mutation arrive on
 * that stream:
 *
 *  - [StoreEvent.Insert] — interpreted in-projection so a single
 *    arriving event can carry NIP-01 / NIP-09 / NIP-62 semantics:
 *
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
 *  - [StoreEvent.Delete] — emitted when a caller invokes
 *    `delete(filter)`, `delete(filters)`, or `deleteExpiredEvents()`
 *    on the [ObservableEventStore]. The projection drops every slot
 *    matching the rule using the same [Filter.match] / NIP-40
 *    expiration semantics the store used. **There is no per-projection
 *    expiration ticker** — projections only drop expired events when
 *    the application calls `deleteExpiredEvents()` on the store.
 *
 * Limit handling: the initial query honours the filter `limit`, and
 * we trim to the same cap when an insert pushes the list over. We do
 * **not** refill from the store after a deletion — if a deletion
 * leaves you under the limit, you stay under the limit until something
 * new arrives. That tradeoff matches what callers were already getting
 * from `LocalCache.observeEvents`.
 *
 * Ephemeral events (kinds `20000-29999`) reach the projection via
 * [ObservableEventStore.events] without ever being persisted; they
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

    /**
     * Slots keyed by replaceable / addressable address for in-place
     * updates and supersession lookups. Plain replaceables (kind 0/3,
     * 10000-19999) live under an [Address] with an empty `dTag`,
     * matching how the SQLite store keys its `addressable_idx` /
     * `replaceable_idx`.
     */
    private val byAddress = HashMap<Address, Slot<T>>()

    /**
     * Sorted view of the same slots. The comparator uses each slot's
     * frozen sort key (the seed event's `created_at` + `id`), so
     * supersession in-place updates never move a slot inside this set.
     */
    private val ordered = sortedSetOf(slotComparator<T>())

    private val limit: Int? = filters.mapNotNull { it.limit }.maxOrNull()

    /** Set when the seed has been written to [items], so callers can suspend until the projection is hot. */
    val ready: CompletableDeferred<Unit> = CompletableDeferred()

    private val collectorJob: Job =
        scope.launch {
            // `onSubscription` runs after the SharedFlow subscription is
            // active but before we pull any events — the buffer absorbs
            // any emissions that arrive while we seed, and we then drain
            // them as the collect proceeds. Doing the seed inside
            // `collect { }` instead would race with concurrent inserts.
            store.events
                .onSubscription {
                    seed()
                    ready.complete(Unit)
                }.collect { storeEvent -> apply(storeEvent) }
        }

    private suspend fun seed() {
        val initial = store.query<T>(filters)
        val now = nowProvider()
        for (event in initial) {
            // Stores don't filter expired events at query time, so we
            // do it here — otherwise an expired event would briefly
            // appear in [items] before the next deleteExpiredEvents()
            // sweep clears it.
            if (isExpiredAt(event, now)) continue
            insertNew(event)
            yield()
        }
        publish()
    }

    private fun apply(storeEvent: StoreEvent) {
        when (storeEvent) {
            is StoreEvent.Insert -> applyInsert(storeEvent.event)
            is StoreEvent.Delete -> applyDelete(storeEvent.rule)
        }
    }

    private fun applyInsert(event: Event) {
        if (isExpiredAt(event, nowProvider())) return

        var changed = false

        // Apply NIP-09 / NIP-62 side effects of the event before we
        // consider matching the event itself against the filter — a
        // deletion event that arrives at the same instant as a
        // matching event still removes its targets.
        if (event is DeletionEvent) {
            if (handleDeletion(event)) changed = true
        }
        if (event is RequestToVanishEvent && event.shouldVanishFrom(relay)) {
            if (handleVanish(event)) changed = true
        }

        if (filters.any { it.match(event) }) {
            if (handleInsert(event)) changed = true
        }

        if (changed) publish()
    }

    private fun applyDelete(rule: DeleteRule) {
        val targets =
            when (rule) {
                is DeleteRule.Filtered -> {
                    if (rule.filters.isEmpty()) return
                    byId.values.filter { slot -> rule.filters.any { it.match(slot.flow.value) } }
                }

                is DeleteRule.Expired -> {
                    val cutoff = rule.asOf ?: nowProvider()
                    byId.values.filter { isExpiredAt(it.flow.value, cutoff) }
                }
            }
        if (targets.isEmpty()) return
        var changed = false
        for (slot in targets) {
            if (removeSlot(slot)) changed = true
        }
        if (changed) publish()
    }

    /**
     * Returns true if the event matches the filter and its arrival
     * caused membership to change (a fresh slot was added). Returns
     * false when the arrival was an in-place supersession update or
     * was rejected by the NIP-01 tiebreaker.
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleInsert(event: Event): Boolean {
        val address = addressOf(event)
        if (address != null) {
            val existing = byAddress[address]
            if (existing != null) {
                if (!supersedes(event, existing.flow.value)) return false

                // Same address, new winner. Rekey byId and update the
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

        insertNew(event)
        return true
    }

    private fun handleDeletion(deletion: DeletionEvent): Boolean {
        var changed = false

        // NIP-09: delete by id, but only if the deletion's author owns
        // the target. For GiftWrap, the owner is the p-tag recipient.
        for (id in deletion.deleteEventIds()) {
            val slot = byId[id] ?: continue
            val ev = slot.flow.value
            val owner = ownerPubKey(ev)
            if (owner == deletion.pubKey && removeSlot(slot)) changed = true
        }

        // NIP-09: delete by address, only original author, only events
        // with `created_at <= deletion.created_at`.
        for (addr in deletion.deleteAddresses()) {
            if (addr.pubKeyHex != deletion.pubKey) continue
            val key = addressOf(addr.kind, addr.pubKeyHex, addr.dTag) ?: continue
            val slot = byAddress[key] ?: continue
            if (slot.flow.value.createdAt <= deletion.createdAt) {
                if (removeSlot(slot)) changed = true
            }
        }

        return changed
    }

    private fun handleVanish(vanish: RequestToVanishEvent): Boolean {
        var changed = false
        // Snapshot first because removeSlot mutates byId.
        val targets =
            byId.values.filter {
                val ev = it.flow.value
                ownerPubKey(ev) == vanish.pubKey && ev.createdAt < vanish.createdAt
            }
        for (slot in targets) {
            if (removeSlot(slot)) changed = true
        }
        return changed
    }

    @Suppress("UNCHECKED_CAST")
    private fun insertNew(event: Event) {
        val slot = Slot(event as T)
        byId[event.id] = slot
        addressOf(event)?.let { byAddress[it] = slot }
        ordered.add(slot)

        val cap = limit ?: return
        while (ordered.size > cap) {
            val tail = ordered.last()
            removeSlot(tail)
        }
    }

    private fun removeSlot(slot: Slot<T>): Boolean {
        val removed = byId.remove(slot.flow.value.id) != null
        if (!removed) return false
        ordered.remove(slot)
        addressOf(slot.flow.value)?.let { address ->
            // Defensive: only clear the address index if this slot
            // still owns it. Could be stale after an addressable rekey
            // raced with another insert.
            if (byAddress[address] === slot) byAddress.remove(address)
        }
        return true
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
        collectorJob.cancel()
        ordered.clear()
        byId.clear()
        byAddress.clear()
        _items.value = emptyList()
    }

    /**
     * Internal slot. Each event added to the projection lives inside
     * one of these for as long as it survives. The sort key is frozen
     * at construction time — supersession in-place updates rewrite
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
         * The lookup [Address] for replaceable / addressable
         * supersession. `null` for regular events (which only collide
         * on event id).
         */
        private fun addressOf(event: Event): Address? = addressOf(event.kind, event.pubKey, (event as? AddressableEvent)?.dTag())

        private fun addressOf(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String?,
        ): Address? =
            when {
                kind.isAddressable() -> Address(kind, pubKeyHex, dTag ?: "")
                kind.isReplaceable() -> Address(kind, pubKeyHex, "")
                else -> null
            }

        /**
         * NIP-01 supersession tiebreaker. The new event wins iff:
         *  - its `created_at` is strictly greater, OR
         *  - the timestamps tie and its `id` is lexically smaller.
         * Otherwise the existing slot keeps its place.
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
        private fun ownerPubKey(event: Event): HexKey = (event as? GiftWrapEvent)?.recipientPubKey() ?: event.pubKey

        private fun isExpiredAt(
            event: Event,
            now: Long,
        ): Boolean {
            val exp = event.expiration() ?: return false
            return exp <= now
        }

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
