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
    // Shapes are hashed on every answer and can carry hundreds of ids: hash once (they never change).
    private var hash = 0

    override fun hashCode(): Int {
        if (hash == 0) {
            var h = ids.hashCode()
            h = 31 * h + authors.hashCode()
            h = 31 * h + kinds.hashCode()
            h = 31 * h + tags.hashCode()
            h = 31 * h + tagsAll.hashCode()
            h = 31 * h + search.hashCode()
            hash = if (h == 0) 1 else h
        }
        return hash
    }

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
 * Who asked: the subscription manager whose REQs a cursor entry describes. A holder filled by several
 * managers (Home's two feeds by one, the notifications feed by the inbox tail and the straggler probe)
 * keeps one entry per manager and relay, so each manager plans against what it sent itself and never
 * overwrites another's: one replacing the other's shapes would make both look uncovered and refetch
 * everything on every pass. Compared by identity; [name] is for logs.
 */
class CursorSource(
    val name: String,
) {
    override fun toString() = name

    companion object {
        /** For callers with a single source, such as tests. */
        val DEFAULT = CursorSource("default")
    }
}

/**
 * The EOSE cursors of one holder of notes: per relay and [CursorSource], the time up to which it has
 * everything that relay sent and the filters that time vouches for. It belongs to the object that keeps those notes
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
        /** What [newEose] was given, by identity: the same request answered again only moves [time]. */
        val sent: List<FilterShape>,
    )

    private class Slot(
        val relay: NormalizedRelayUrl,
        val source: CursorSource,
    ) {
        override fun equals(other: Any?) = other is Slot && other.relay == relay && other.source === source

        override fun hashCode() = relay.hashCode() * 31 + source.hashCode()
    }

    private val lock = KmpLock()
    private val byRelay = mutableMapOf<Slot, Entry>()

    // The `since` values recently planned for this holder, per relay and source: an answer to a
    // filter asked with one of them is an answer to this holder's own plan. A manager can plan one
    // relay in several passes (one per filter group), so they add up, keeping the last few.
    private val planned = mutableMapOf<Slot, ArrayDeque<Long>>()

    /** [source] asked [relay], on this holder's behalf, with these `since` values. */
    fun planned(
        relay: NormalizedRelayUrl,
        source: CursorSource,
        windows: Set<Long>,
    ) = lock.withLock {
        if (windows.isEmpty()) return@withLock
        val recent = planned.getOrPut(Slot(relay, source)) { ArrayDeque() }
        windows.forEach { window ->
            if (window !in recent) {
                recent.addLast(window)
                if (recent.size > MAX_PLANNED) recent.removeFirst()
            }
        }
    }

    /**
     * Whether an answer to [shape] asked `since` [since] tells this holder it has everything up to
     * the answer's time: the filter asked from scratch, or from no later than a window planned for
     * this holder, or from no later than this holder's own cursor already covers. A filter planned
     * for someone else (the feed this one replaced, another account in a merged REQ) only fetched
     * what *they* lacked. "No later than" because the client does not resend a REQ whose `since`
     * only moved forward: the filter still on the wire asks from earlier than the newest plan,
     * which is more, not less.
     */
    fun accepts(
        relay: NormalizedRelayUrl,
        source: CursorSource,
        shape: FilterShape,
        since: Long?,
    ): Boolean {
        if (since == null) return true
        val slot = Slot(relay, source)
        return lock.withLock {
            planned[slot]?.any { window -> since <= window } == true ||
                byRelay[slot]?.let { entry ->
                    val askable = RefusedKinds.shared.askable(relay, shape)
                    entry.time >= since && entry.shapes.any { stored -> stored.covers(askable) }
                } == true
        }
    }

    /**
     * The raw time for [relay] as [source] left it, or the latest of any source when [source] is
     * null; null when nothing is vouched for there.
     */
    fun since(
        relay: NormalizedRelayUrl,
        source: CursorSource? = null,
    ): Long? =
        lock.withLock {
            if (source != null) {
                byRelay[Slot(relay, source)]?.time
            } else {
                byRelay.entries.filter { it.key.relay == relay }.maxOfOrNull { it.value.time }
            }
        }

    fun isEmpty(): Boolean = lock.withLock { byRelay.isEmpty() }

    /** Every relay [source] has a time for, with that time. */
    fun sinceByRelay(source: CursorSource): Map<NormalizedRelayUrl, Long> =
        lock.withLock {
            byRelay.entries.filter { it.key.source === source }.associate { it.key.relay to it.value.time }
        }

    /**
     * How to ask [relay] for [requested]: one part `since` the cursor when a stored filter covers
     * it; a covered part and a from-scratch part for authors the stored filter did not have; or the
     * whole request from scratch.
     */
    fun plan(
        relay: NormalizedRelayUrl,
        asked: FilterShape,
        source: CursorSource = CursorSource.DEFAULT,
    ): List<CursorPart> {
        // The kinds this relay refused are never sent, so no cursor can have vouched for them.
        val requested = RefusedKinds.shared.askable(relay, asked)
        val entry = lock.withLock { byRelay[Slot(relay, source)] } ?: return listOf(CursorPart(requested, null))
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
        source: CursorSource = CursorSource.DEFAULT,
    ) = lock.withLock {
        val slot = Slot(relay, source)
        val before = byRelay[slot]
        // Every live event re-reports the request on the wire: the same list, nothing to merge again.
        byRelay[slot] = if (before != null && before.sent === sent) Entry(time, before.shapes, sent) else Entry(time, mergeByAuthors(sent), sent)
    }

    /**
     * Filters that differ only in their authors become one with all of them: a request split by
     * cursor (the covered authors and the new ones) vouches for the whole request once answered.
     */
    private fun mergeByAuthors(shapes: List<FilterShape>): List<FilterShape> {
        val merged = LinkedHashMap<FilterShape, FilterShape>()
        shapes.forEach { shape ->
            val authors = shape.authors
            if (authors == null) {
                merged[shape] = shape
                return@forEach
            }
            val rest = shape.copy(authors = null)
            val before = merged[rest]
            merged[rest] = if (before?.authors != null) before.copy(authors = before.authors + authors) else shape
        }
        return merged.values.toList()
    }

    /**
     * Notes created at [createdAt] or later were dropped: every relay asks again from there. The
     * windows planned later than that go too: an answer to a REQ still on the wire that asks from
     * after [createdAt] did not fetch the dropped band, and accepting it would undo the rewind.
     */
    fun rewindTo(createdAt: Long) {
        lock.withLock {
            byRelay.keys.toList().forEach { slot ->
                val entry = byRelay.getValue(slot)
                if (entry.time > createdAt) byRelay[slot] = Entry(createdAt, entry.shapes, entry.sent)
            }
            planned.values.forEach { windows -> windows.removeAll { it > createdAt } }
        }
        replanServers()
    }

    /**
     * Vouches for nothing: every relay is asked from scratch. The managers serving this holder plan
     * again right away; a live REQ would otherwise keep its old window, and relays never resend
     * what is older than it.
     */
    fun clear() {
        val hadAny =
            lock.withLock {
                (byRelay.isNotEmpty()).also {
                    byRelay.clear()
                    planned.clear()
                }
            }
        if (hadAny) replanServers()
    }

    // The managers whose subscriptions serve this holder, with how many of their subscriptions do.
    // Managers live as long as the app; this never references the holder.
    private val servers = mutableMapOf<IEoseManager, Int>()

    /** [manager] now serves this holder from one more subscription. */
    fun addServer(manager: IEoseManager) = lock.withLock { servers[manager] = (servers[manager] ?: 0) + 1 }

    /** [manager] serves this holder from one subscription fewer. */
    fun removeServer(manager: IEoseManager) =
        lock.withLock {
            val left = (servers[manager] ?: return@withLock) - 1
            if (left <= 0) servers.remove(manager) else servers[manager] = left
        }

    private fun replanServers() = lock.withLock { servers.keys.toList() }.forEach { it.invalidateFilters() }
}

private const val MAX_PLANNED = 16

/** Keeps notes, and so the [RelayCursors] that vouch for them. */
interface RelayCursorHolder {
    val relayCursors: RelayCursors
}
