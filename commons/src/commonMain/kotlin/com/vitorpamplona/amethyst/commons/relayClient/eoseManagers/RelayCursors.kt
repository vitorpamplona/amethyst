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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * What a filter asks for, without the window (`since`, `until`, `limit`): the part a cursor can
 * vouch for. A null field is unrestricted.
 */
data class FilterShape(
    val ids: Set<String>? = null,
    val authors: Set<HexKey>? = null,
    val kinds: Set<Int>? = null,
    val tags: Map<String, Set<String>>? = null,
    val tagsAll: Map<String, Set<String>>? = null,
    val search: String? = null,
) {
    /** Whether everything [other] asks for was asked for by this one too. */
    fun covers(other: FilterShape): Boolean = coversExceptAuthors(other) && within(other.authors, authors)

    /** As [covers], for every field but the authors. */
    fun coversExceptAuthors(other: FilterShape): Boolean =
        within(other.ids, ids) &&
            within(other.kinds, kinds) &&
            tagsWithin(other.tags, tags) &&
            other.tagsAll == tagsAll &&
            other.search == search

    companion object {
        fun of(filter: Filter) =
            FilterShape(
                ids = filter.ids?.toSet(),
                authors = filter.authors?.toSet(),
                kinds = filter.kinds?.toSet(),
                tags = filter.tags?.mapValues { it.value.toSet() },
                tagsAll = filter.tagsAll?.mapValues { it.value.toSet() },
                search = filter.search,
            )

        /** [inner] restricts at least as much as [outer]: a null [outer] restricts nothing. */
        private fun <T> within(
            inner: Set<T>?,
            outer: Set<T>?,
        ) = outer == null || (inner != null && outer.containsAll(inner))

        /** Tag filters are ANDed across keys: every key [outer] requires, [inner] requires as narrowly. */
        private fun tagsWithin(
            inner: Map<String, Set<String>>?,
            outer: Map<String, Set<String>>?,
        ): Boolean {
            if (outer == null) return true
            if (inner == null) return outer.isEmpty()
            return outer.all { (key, values) -> inner[key]?.let { values.containsAll(it) } == true }
        }
    }
}

/**
 * One part of a request, as a cursor sees it: [shape] asked `since` [since], or from scratch when
 * [since] is null.
 */
data class CursorPart(
    val shape: FilterShape,
    val since: Long?,
)

/**
 * The EOSE cursors of one holder of notes: per relay, the time up to which it has everything that
 * relay sent and the filters that time vouches for. It belongs to the object that keeps those notes
 * (a feed, a [com.vitorpamplona.amethyst.commons.model.Note]'s children, an account's lists) and
 * dies with it, because the claim is only true while the notes are held: `LocalCache` keeps none
 * on its own. It never references its holder.
 *
 * Narrowing a request keeps the cursor. Widening it by authors asks only the new authors from
 * scratch; any other widening asks the whole request from scratch. See
 * `commons/plans/2026-11-09-account-feed-lifecycle.md`, section 1.
 */
class RelayCursors {
    private class Entry(
        val time: Long,
        val shapes: List<FilterShape>,
    )

    private val lock = KmpLock()
    private val byRelay = mutableMapOf<NormalizedRelayUrl, Entry>()

    /** The raw time for [relay], or null when nothing is vouched for there. */
    fun since(relay: NormalizedRelayUrl): Long? = lock.withLock { byRelay[relay]?.time }

    fun isEmpty(): Boolean = lock.withLock { byRelay.isEmpty() }

    /**
     * How to ask [relay] for [requested]: one part `since` the cursor when a stored filter covers
     * it; a covered part and a from-scratch part for authors the stored filter did not have; or the
     * whole request from scratch.
     */
    fun plan(
        relay: NormalizedRelayUrl,
        requested: FilterShape,
    ): List<CursorPart> {
        val entry = lock.withLock { byRelay[relay] } ?: return listOf(CursorPart(requested, null))
        if (entry.shapes.any { it.covers(requested) }) return listOf(CursorPart(requested, entry.time))

        val requestedAuthors = requested.authors
        if (requestedAuthors != null) {
            entry.shapes.forEach { stored ->
                val storedAuthors = stored.authors ?: return@forEach
                if (!stored.coversExceptAuthors(requested)) return@forEach
                val known = requestedAuthors.intersect(storedAuthors)
                if (known.isEmpty()) return@forEach
                return listOf(
                    CursorPart(requested.copy(authors = known), entry.time),
                    CursorPart(requested.copy(authors = requestedAuthors - known), null),
                )
            }
        }
        return listOf(CursorPart(requested, null))
    }

    /**
     * [relay] has sent everything for [sent] up to [time] (its EOSE, or a live event after it). The
     * cursor now vouches for exactly [sent]: narrower than before drops what left, and what left
     * may no longer be held.
     */
    fun newEose(
        relay: NormalizedRelayUrl,
        sent: List<FilterShape>,
        time: Long,
    ) = lock.withLock {
        byRelay[relay] = Entry(time, sent)
    }

    /** Notes created at [createdAt] or later were dropped: every relay asks again from there. */
    fun rewindTo(createdAt: Long) =
        lock.withLock {
            byRelay.keys.toList().forEach { relay ->
                val entry = byRelay.getValue(relay)
                if (entry.time > createdAt) byRelay[relay] = Entry(createdAt, entry.shapes)
            }
        }

    /** Vouches for nothing: every relay is asked from scratch. */
    fun clear() = lock.withLock { byRelay.clear() }
}

/** Keeps notes, and so the [RelayCursors] that vouch for them. */
interface RelayCursorHolder {
    val relayCursors: RelayCursors
}
