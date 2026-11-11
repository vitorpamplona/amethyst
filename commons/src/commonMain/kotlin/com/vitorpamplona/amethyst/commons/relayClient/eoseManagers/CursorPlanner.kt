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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A query key that names the holders of the notes its subscription loads: their [RelayCursors] set
 * the windows of its filters and receive its EOSEs. A key that does not implement it (or names none)
 * keeps the manager's own cursor map. See `commons/plans/2026-11-09-account-feed-lifecycle.md`,
 * section 1.
 */
interface CursorHoldingQuery {
    val cursorHolders: List<RelayCursorHolder>
}

object CursorPlanner {
    /**
     * The windows of a live request for [holders]: each filter without `until` is split into the
     * parts the holders' cursors call for, each `since` the later of the filter's own floor and the
     * part's cursor. Filters with `until` ask for older pages, which no cursor governs: unchanged.
     */
    fun window(
        filters: List<RelayBasedFilter>,
        holders: List<RelayCursors>,
    ): List<RelayBasedFilter> =
        filters.flatMap { relayFilter ->
            val filter = relayFilter.filter
            if (filter.until != null) return@flatMap listOf(relayFilter)
            val shape = FilterShape.of(filter)
            plan(relayFilter.relay, shape, holders).map { part ->
                RelayBasedFilter(relayFilter.relay, part.applyTo(filter, shape))
            }
        }

    /**
     * One plan for several holders: every author (or the whole request, without authors) is asked
     * from the oldest cursor any holder has for it, from scratch if one has none, so no holder misses
     * anything; authors with the same window share a part. A holder keeps one time per relay, so
     * an author is either covered by every holder (the oldest of their times) or asked from scratch:
     * at most two parts per filter, well within what relays allow per REQ.
     */
    fun plan(
        relay: NormalizedRelayUrl,
        requested: FilterShape,
        holders: List<RelayCursors>,
    ): List<CursorPart> {
        if (holders.size == 1) return holders[0].plan(relay, requested)
        val plans = holders.map { it.plan(relay, requested) }

        val authors = requested.authors
        if (authors == null) {
            return listOf(CursorPart(requested, oldest(plans.map { parts -> parts.singleOrNull()?.since })))
        }

        val windowByAuthor =
            authors.associateWith { author ->
                oldest(plans.map { parts -> parts.firstOrNull { author in it.shape.authors.orEmpty() }?.since })
            }
        return windowByAuthor.entries
            .groupBy({ it.value }, { it.key })
            .map { (since, group) -> CursorPart(requested.copy(authors = group.toSet()), since) }
            .sortedWith(compareByDescending(nullsFirst()) { it.since })
    }

    /**
     * The windows of one relay's request that names several members (accounts in a `#p` filter),
     * each with its own holders: every member is planned against its own cursors on top of its own
     * floor, and members whose windows agree share a filter. [build] makes the relay's filters for a
     * set of members from one `since`, in the same order whatever the members, so a member that is new
     * (or back with an empty feed) is asked from scratch without making the others refetch, and one
     * that joins a relay inherits no other member's cursor.
     */
    fun <M> windowPerMember(
        members: List<Member<M>>,
        build: (members: List<M>, since: Long?) -> List<RelayBasedFilter>,
    ): List<RelayBasedFilter> {
        // (which filter of [build], its `since`) -> the members asking it with that window.
        val shared = LinkedHashMap<Pair<Int, Long?>, MutableList<M>>()
        val unshared = mutableListOf<RelayBasedFilter>()

        members.forEach { member ->
            val own = build(listOf(member.id), member.floor)
            val windowed = window(own, member.holders)
            // A filter split by authors no longer lines up with [build]'s order: sent as planned.
            if (windowed.size != own.size) {
                unshared.addAll(windowed)
            } else {
                windowed.forEachIndexed { index, it -> shared.getOrPut(index to it.filter.since) { mutableListOf() }.add(member.id) }
            }
        }

        return shared.map { (slot, ids) -> build(ids, slot.second)[slot.first] } + unshared
    }

    /** One member of a merged request: [floor] is its own lower bound, [holders] its own cursors. */
    class Member<M>(
        val id: M,
        val floor: Long?,
        val holders: List<RelayCursors>,
    )

    /** The oldest of [windows]; null (from scratch) when any of them is. */
    private fun oldest(windows: List<Long?>): Long? = if (windows.any { it == null }) null else windows.filterNotNull().minOrNull()

    private fun CursorPart.applyTo(
        filter: Filter,
        requested: FilterShape,
    ): Filter {
        val floor = filter.since
        val window =
            if (since == null) {
                floor
            } else if (floor == null) {
                since
            } else {
                maxOf(floor, since)
            }
        val authors = if (shape.authors == requested.authors) filter.authors else shape.authors?.toList()
        return filter.copy(authors = authors, since = window)
    }

    /**
     * What [relay] answered for the live filters in [sent], up to [time], goes to every holder in
     * [holders]. Older-page filters (with `until`) vouch for no cursor.
     */
    fun record(
        holders: List<RelayCursors>,
        relay: NormalizedRelayUrl,
        sent: List<Filter>?,
        time: Long,
    ) {
        if (holders.isEmpty() || sent.isNullOrEmpty()) return
        val live = sent.filter { it.until == null }.map { FilterShape.of(it) }
        if (live.isEmpty()) return
        holders.forEach { it.newEose(relay, live, time) }
    }
}

/**
 * Wraps a subscription's listener so its EOSEs and live events reach the cursors of the holders the
 * subscription serves at that moment ([holders], read on each call), not the ones that existed when
 * it opened. Everything else goes to [listener] unchanged.
 */
class CursorRoutingListener(
    private val listener: SubscriptionListener,
    private val holders: () -> List<RelayCursors>,
) : SubscriptionListener by listener {
    override fun onEose(
        relay: NormalizedRelayUrl,
        forFilters: List<Filter>?,
    ) {
        CursorPlanner.record(holders(), relay, forFilters, TimeUtils.now())
        listener.onEose(relay, forFilters)
    }

    override fun onEose(
        relay: NormalizedRelayUrl,
        forFilters: List<Filter>?,
        eventIds: List<String>?,
    ) {
        CursorPlanner.record(holders(), relay, forFilters, TimeUtils.now())
        listener.onEose(relay, forFilters, eventIds)
    }

    override suspend fun onEvent(
        event: Event,
        isLive: Boolean,
        relay: NormalizedRelayUrl,
        forFilters: List<Filter>?,
    ) {
        if (isLive) CursorPlanner.record(holders(), relay, forFilters, TimeUtils.now())
        listener.onEvent(event, isLive, relay, forFilters)
    }
}
