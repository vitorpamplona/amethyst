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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Inverted index over a population of [Filter]-bearing subscribers.
 * Given an [Event], returns the (much smaller) set of subscribers
 * whose filters could match — callers still run [Filter.match] on
 * each candidate to enforce negative constraints (`since` / `until`
 * / `tagsAll` / etc.) but skip the per-event walk over subscribers
 * that share no narrowing field with the event.
 *
 * Used by the relay server's `LiveEventStore` for fanout from one
 * inserted event to many `REQ` subscriptions, and by the client-side
 * `LocalCache.observables` registry for the same shape inside the
 * app (one accepted event, many feed observers).
 *
 * ## Indexing strategy
 *
 * Each registered filter contributes entries to **one** dimension —
 * the most selective indexable field. Picking one dimension instead
 * of all of them avoids over-counting subscribers in [candidatesFor]
 * (no `Set` dedup work) and minimises bucket churn on register /
 * unregister:
 *
 * 1. `ids` (most selective; an id matches one event).
 * 2. `authors`.
 * 3. The first single-letter tag in `tags` (then `tagsAll`).
 * 4. `kinds`.
 * 5. None of the above → registered into [unindexedKey].
 *
 * Multi-filter registrations OR the per-filter selections together
 * (a subscriber matches if *any* of its filters matches the event,
 * which mirrors `filters.any { it.match(...) }`).
 *
 * ## Concurrency
 *
 * State is held in a single [AtomicReference] and mutated via
 * copy-on-write CAS loops, mirroring the
 * `nip86RelayManagement.server.BanStore` pattern. Reads in
 * [candidatesFor] and [forEach] are wait-free single-load atomic.
 * Writes (subscription register / unregister) copy the inner maps
 * — fine for this workload because writes are subscription-rate
 * (rare) while reads are event-rate (frequent).
 *
 * ## What the index does NOT cover
 *
 *  - Negative filter constraints (`since`, `until`, `tagsAll`,
 *    `limit`-already-saturated). The candidate set is a
 *    super-set; callers must still run [Filter.match] on each
 *    candidate.
 *  - Subscribers driven by an arbitrary `(Event) -> Boolean`
 *    predicate without an underlying [Filter]. Use
 *    [registerUnindexed] for those — they're returned for every
 *    event.
 *  - Membership-driven re-evaluation paths (e.g. an addressable
 *    `v2` that no longer matches a filter but the observer
 *    already holds `v1`). Those callers must consult their own
 *    membership state in addition to [candidatesFor].
 */
@OptIn(ExperimentalAtomicApi::class)
class FilterIndex<S : Any> {
    /**
     * Bucket key. Five concrete shapes plus a sentinel for filters
     * with no indexable narrowing field.
     */
    private sealed interface BucketKey

    private data class IdKey(
        val id: HexKey,
    ) : BucketKey

    private data class AuthorKey(
        val author: HexKey,
    ) : BucketKey

    private data class TagKey(
        val letter: String,
        val value: String,
    ) : BucketKey

    private data class KindKey(
        val kind: Int,
    ) : BucketKey

    private object Unindexed : BucketKey

    /**
     * Single immutable snapshot. [buckets] maps a key to the set of
     * subscribers registered under it; [assignments] is the reverse
     * map used by [unregister] to find a subscriber's keys without
     * scanning every bucket.
     */
    private data class State<S>(
        val buckets: Map<BucketKey, Set<S>> = emptyMap(),
        val assignments: Map<S, Set<BucketKey>> = emptyMap(),
    )

    private val state: AtomicReference<State<S>> = AtomicReference(State())

    /** Number of distinct subscribers currently registered. */
    fun size(): Int = state.load().assignments.size

    fun isEmpty(): Boolean = state.load().assignments.isEmpty()

    /**
     * Register [subscriber] under the bucket(s) selected for [filter].
     * If [filter] has no indexable field the subscriber is added to
     * the unindexed pool and is returned for every event.
     */
    fun register(
        filter: Filter,
        subscriber: S,
    ) {
        val keys = selectKeys(filter).ifEmpty { listOf(Unindexed) }
        addAssignments(subscriber, keys)
    }

    /**
     * Register [subscriber] for a list of filters (OR semantics).
     * Each filter's most-selective dimension contributes its keys;
     * any filter with no indexable field adds the subscriber to the
     * unindexed pool, which dominates dispatch (the subscriber
     * matches every event).
     */
    fun register(
        filters: List<Filter>,
        subscriber: S,
    ) {
        if (filters.isEmpty()) {
            addAssignments(subscriber, listOf(Unindexed))
            return
        }
        val keys = mutableListOf<BucketKey>()
        for (f in filters) {
            val perFilter = selectKeys(f)
            if (perFilter.isEmpty()) {
                keys.add(Unindexed)
            } else {
                keys.addAll(perFilter)
            }
        }
        addAssignments(subscriber, keys)
    }

    /**
     * Register [subscriber] in the unindexed pool. Use this for
     * subscribers driven by an opaque predicate where the index
     * can't infer a narrowing field.
     */
    fun registerUnindexed(subscriber: S) = addAssignments(subscriber, listOf(Unindexed))

    /**
     * Remove [subscriber] from every bucket it was registered in.
     * No-op if the subscriber isn't currently registered.
     */
    fun unregister(subscriber: S) {
        while (true) {
            val current = state.load()
            val keys = current.assignments[subscriber] ?: return
            val newBuckets = current.buckets.toMutableMap()
            for (key in keys) {
                val cur = newBuckets[key] ?: continue
                val next = cur - subscriber
                if (next.isEmpty()) {
                    newBuckets.remove(key)
                } else {
                    newBuckets[key] = next
                }
            }
            val newAssignments = current.assignments - subscriber
            if (state.compareAndSet(current, State(newBuckets, newAssignments))) return
        }
    }

    /**
     * Subscribers whose filters might match [event]. The result is a
     * super-set: callers must still run `filter.match(event)` on each
     * candidate to handle negative constraints.
     *
     * Iteration order is insertion-stable per call but otherwise
     * unspecified.
     */
    fun candidatesFor(event: Event): Set<S> {
        val s = state.load()
        if (s.buckets.isEmpty()) return emptySet()
        val result = LinkedHashSet<S>()
        s.buckets[Unindexed]?.let { result.addAll(it) }
        s.buckets[IdKey(event.id)]?.let { result.addAll(it) }
        s.buckets[AuthorKey(event.pubKey)]?.let { result.addAll(it) }
        s.buckets[KindKey(event.kind)]?.let { result.addAll(it) }
        for (tag in event.tags) {
            if (tag.size >= 2 && tag[0].length == 1) {
                s.buckets[TagKey(tag[0], tag[1])]?.let { result.addAll(it) }
            }
        }
        return result
    }

    /**
     * Visit every registered subscriber. Used by callers that need
     * to broadcast something the index can't help with (e.g.
     * `LocalCache.refreshDeletedNoteObservers` — the deletion path
     * has no event-shape to consult, every observer must see it).
     */
    fun forEach(action: (S) -> Unit) {
        for (sub in state.load().assignments.keys) action(sub)
    }

    private fun addAssignments(
        subscriber: S,
        keys: List<BucketKey>,
    ) {
        if (keys.isEmpty()) return
        val keySet = keys.toSet()
        while (true) {
            val current = state.load()
            val newBuckets = current.buckets.toMutableMap()
            for (key in keySet) {
                val cur = newBuckets[key] ?: emptySet()
                if (subscriber in cur) continue
                newBuckets[key] = cur + subscriber
            }
            val existing = current.assignments[subscriber]
            val merged = if (existing == null) keySet else existing + keySet
            val newAssignments = current.assignments + (subscriber to merged)
            if (state.compareAndSet(current, State(newBuckets, newAssignments))) return
        }
    }

    /**
     * Pick the most-selective indexable dimension for [filter] and
     * expand it into one [BucketKey] per value. Returns an empty
     * list if no field is indexable — caller maps that to [Unindexed].
     */
    private fun selectKeys(filter: Filter): List<BucketKey> {
        if (!filter.ids.isNullOrEmpty()) {
            return filter.ids.map { IdKey(it) }
        }
        if (!filter.authors.isNullOrEmpty()) {
            return filter.authors.map { AuthorKey(it) }
        }
        if (!filter.tags.isNullOrEmpty()) {
            val first =
                filter.tags.entries.firstOrNull {
                    it.key.length == 1 && it.value.isNotEmpty()
                }
            if (first != null) return first.value.map { TagKey(first.key, it) }
        }
        if (!filter.tagsAll.isNullOrEmpty()) {
            val first =
                filter.tagsAll.entries.firstOrNull {
                    it.key.length == 1 && it.value.isNotEmpty()
                }
            if (first != null) return first.value.map { TagKey(first.key, it) }
        }
        if (!filter.kinds.isNullOrEmpty()) {
            return filter.kinds.map { KindKey(it) }
        }
        return emptyList()
    }
}
