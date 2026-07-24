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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.toPersistentHashSet
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
 * Writes (subscription register / unregister) build the next snapshot
 * from persistent (HAMT) maps — O(keys × log S) with structural
 * sharing rather than a full O(S) copy of both maps, since on a relay
 * a write happens on every REQ open and close.
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
     * Single immutable snapshot. Subscribers are held in one map per
     * indexable dimension so [candidatesFor] — called once per accepted
     * ingest event, the hot read — can probe each dimension with the
     * event's own field (`event.id`, `event.pubKey`, `tag[0]`/`tag[1]`,
     * `event.kind`) and allocate no key-wrapper objects. [assignments] is
     * the reverse map ([S] → the [BucketKey]s it occupies) used by
     * [unregister]; the wrappers live only here, built on the rare
     * register path.
     *
     * Persistent (HAMT) maps/sets: a register/unregister produces the
     * next snapshot in O(keys × log S) with structural sharing, instead
     * of copying full maps — registration happens on every REQ open/close.
     */
    private data class State<S>(
        val ids: PersistentMap<HexKey, PersistentSet<S>> = persistentHashMapOf(),
        val authors: PersistentMap<HexKey, PersistentSet<S>> = persistentHashMapOf(),
        val tags: PersistentMap<String, PersistentMap<String, PersistentSet<S>>> = persistentHashMapOf(),
        val kinds: PersistentMap<Int, PersistentSet<S>> = persistentHashMapOf(),
        val unindexed: PersistentSet<S> = persistentHashSetOf(),
        val assignments: PersistentMap<S, PersistentSet<BucketKey>> = persistentHashMapOf(),
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
            var ids = current.ids
            var authors = current.authors
            var tags = current.tags
            var kinds = current.kinds
            var unindexed = current.unindexed
            for (key in keys) {
                when (key) {
                    is IdKey -> ids = ids.removeSub(key.id, subscriber)
                    is AuthorKey -> authors = authors.removeSub(key.author, subscriber)
                    is KindKey -> kinds = kinds.removeSub(key.kind, subscriber)
                    is TagKey -> tags = tags.removeTagSub(key.letter, key.value, subscriber)
                    Unindexed -> unindexed = unindexed.removing(subscriber)
                }
            }
            val next =
                State(ids, authors, tags, kinds, unindexed, current.assignments.removing(subscriber))
            if (state.compareAndSet(current, next)) return
        }
    }

    /**
     * Subscribers whose filters might match [event]. The result is a
     * super-set: callers must still run `filter.match(event)` on each
     * candidate to handle negative constraints.
     *
     * Iteration order is insertion-stable per call but otherwise
     * unspecified. Allocates only the result set — dimensions are
     * probed with the event's own fields, no key wrappers.
     */
    fun candidatesFor(event: Event): Set<S> {
        val s = state.load()
        if (s.assignments.isEmpty()) return emptySet()
        val result = LinkedHashSet<S>()
        if (s.unindexed.isNotEmpty()) result.addAll(s.unindexed)
        s.ids[event.id]?.let { result.addAll(it) }
        s.authors[event.pubKey]?.let { result.addAll(it) }
        s.kinds[event.kind]?.let { result.addAll(it) }
        if (s.tags.isNotEmpty()) {
            for (tag in event.tags) {
                if (tag.size >= 2 && tag[0].length == 1) {
                    s.tags[tag[0]]?.get(tag[1])?.let { result.addAll(it) }
                }
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
        val keySet = keys.toPersistentHashSet()
        while (true) {
            val current = state.load()
            var ids = current.ids
            var authors = current.authors
            var tags = current.tags
            var kinds = current.kinds
            var unindexed = current.unindexed
            for (key in keySet) {
                when (key) {
                    is IdKey -> ids = ids.addSub(key.id, subscriber)
                    is AuthorKey -> authors = authors.addSub(key.author, subscriber)
                    is KindKey -> kinds = kinds.addSub(key.kind, subscriber)
                    is TagKey -> tags = tags.addTagSub(key.letter, key.value, subscriber)
                    Unindexed -> unindexed = unindexed.adding(subscriber)
                }
            }
            val existing = current.assignments[subscriber]
            val merged = existing?.addingAll(keySet) ?: keySet
            val next = State(ids, authors, tags, kinds, unindexed, current.assignments.putting(subscriber, merged))
            if (state.compareAndSet(current, next)) return
        }
    }

    // Per-dimension add/remove of one subscriber, returning the same map
    // instance when nothing changed so the CAS builds minimal new nodes.
    private fun <K> PersistentMap<K, PersistentSet<S>>.addSub(
        key: K,
        sub: S,
    ): PersistentMap<K, PersistentSet<S>> {
        val cur = this[key] ?: persistentHashSetOf()
        val next = cur.adding(sub)
        return if (next === cur) this else putting(key, next)
    }

    private fun <K> PersistentMap<K, PersistentSet<S>>.removeSub(
        key: K,
        sub: S,
    ): PersistentMap<K, PersistentSet<S>> {
        val cur = this[key] ?: return this
        val next = cur.removing(sub)
        return when {
            next === cur -> this
            next.isEmpty() -> removing(key)
            else -> putting(key, next)
        }
    }

    private fun PersistentMap<String, PersistentMap<String, PersistentSet<S>>>.addTagSub(
        letter: String,
        value: String,
        sub: S,
    ): PersistentMap<String, PersistentMap<String, PersistentSet<S>>> {
        val inner = this[letter] ?: persistentHashMapOf()
        val newInner = inner.addSub(value, sub)
        return if (newInner === inner) this else putting(letter, newInner)
    }

    private fun PersistentMap<String, PersistentMap<String, PersistentSet<S>>>.removeTagSub(
        letter: String,
        value: String,
        sub: S,
    ): PersistentMap<String, PersistentMap<String, PersistentSet<S>>> {
        val inner = this[letter] ?: return this
        val newInner = inner.removeSub(value, sub)
        return when {
            newInner === inner -> this
            newInner.isEmpty() -> removing(letter)
            else -> putting(letter, newInner)
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
