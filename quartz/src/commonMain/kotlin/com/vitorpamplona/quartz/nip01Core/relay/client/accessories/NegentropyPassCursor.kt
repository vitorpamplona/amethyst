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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Where [negentropySync] stands in its walk back past a relay that reconciles only part of
 * what it holds (it builds the reconcile from its newest N matches and says nothing).
 *
 * A pass reconciles [window]. The cursor sees what the pass delivered ([accept]) and how far
 * back the relay matched what we already hold ([matched]); [endPass] then says whether another
 * pass could make progress, and over which window. That pass runs only if a probe finds the
 * relay holds something there we lack, and [advance] commits it.
 *
 * Passes overlap by one second: the relay may have cut its set inside the second it reached,
 * so the next window reaches back to it inclusively. What earlier passes delivered there is
 * handed to the next reconcile as ours ([carriedOver]), so the relay does not name it again.
 *
 * Every pass must reach strictly further back, or add events at the same second. Anything else
 * is a relay that ignores the window, and asking again would loop.
 *
 * Driven from one coroutine, except [matched], which reconcilers may call concurrently.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class NegentropyPassCursor(
    filter: Filter,
) {
    /** The window the current pass reconciles. */
    var window: Filter = filter
        private set

    /** Passes ended so far. */
    var passes = 0
        private set

    // The second the previous pass reached, and what earlier passes delivered there.
    private var boundary: Long? = null
    private var boundaryIds: Set<HexKey> = emptySet()

    // This pass.
    private var oldest = Long.MAX_VALUE
    private var atOldest = HashSet<HexKey>()
    private var fresh = 0
    private val matchedLocal = AtomicLong(Long.MAX_VALUE)

    /** What earlier passes delivered at the window's top second, as entries we hold. */
    fun carriedOver(): List<IdAndTime> {
        val second = boundary ?: return emptyList()
        return boundaryIds.map { IdAndTime(second, it) }
    }

    /**
     * Whether [event] is this pass's to deliver: not above the window (only a relay that ignores
     * `until` sends one, and an earlier pass covered that range) and not one an earlier pass
     * already delivered.
     */
    fun accept(event: Event): Boolean {
        val until = if (passes > 0) window.until else null
        if (until != null && event.createdAt > until) return false
        if (event.createdAt == boundary && event.id in boundaryIds) return false
        fresh++
        if (event.createdAt < oldest) {
            oldest = event.createdAt
            atOldest = HashSet()
        }
        if (event.createdAt == oldest) atOldest.add(event.id)
        return true
    }

    /** The relay matched an entry of ours created at [createdAt] (one it did not report as a have). */
    fun matched(createdAt: Long) {
        while (true) {
            val now = matchedLocal.load()
            if (createdAt >= now || matchedLocal.compareAndSet(now, createdAt)) return
        }
    }

    /**
     * How far back this pass's reconcile reached: its oldest delivery or the oldest entry of
     * ours the relay matched. [Long.MAX_VALUE] when the relay matched nothing at all.
     */
    val reach: Long get() = minOf(oldest, matchedLocal.load())

    /** The window a next pass would reconcile, and the ids known at its top second. */
    class Next(
        val window: Filter,
        val known: Set<HexKey>,
    )

    /** Ends the pass: the next window worth probing, or null when no pass could make progress. */
    fun endPass(): Next? {
        passes++
        val reach = reach
        val prior = boundary
        if (reach == Long.MAX_VALUE) return null
        if (prior != null && (reach > prior || (reach == prior && fresh == 0))) return null
        val known = HashSet<HexKey>()
        if (oldest == reach) known.addAll(atOldest)
        if (reach == prior) known.addAll(boundaryIds)
        return Next(window.copy(until = reach), known)
    }

    /** Starts the pass [next] describes. */
    fun advance(next: Next) {
        boundary = next.window.until
        boundaryIds = next.known
        window = next.window
        oldest = Long.MAX_VALUE
        atOldest = HashSet()
        fresh = 0
        matchedLocal.store(Long.MAX_VALUE)
    }
}

/**
 * Haves found by passes that may yet be re-reconciled. A have dated after the pass's [reach] is
 * settled (the relay's reconcile covered that second and lacked it); one at or below it is only
 * as good as a reconcile the relay may have cut short there, and the next pass, which covers
 * that range again, decides it.
 */
internal fun List<IdAndTime>.settledAbove(reach: Long): Pair<List<IdAndTime>, List<IdAndTime>> = partition { it.createdAt > reach }

/** [this] plus [extra], which the caller holds too but which [this] does not list. */
internal fun NegentropyLocalIndex.plus(extra: List<IdAndTime>): NegentropyLocalIndex {
    if (extra.isEmpty()) return this
    val base = this
    return object : NegentropyLocalIndex {
        override suspend fun count(window: Filter): Int? = base.count(window)?.let { it + extra.count { e -> e.inside(window) } }

        override suspend fun entriesFor(window: Filter): List<IdAndTime> = base.entriesFor(window) + extra.filter { it.inside(window) }
    }
}

private fun IdAndTime.inside(window: Filter): Boolean {
    val since = window.since
    val until = window.until
    return (since == null || createdAt >= since) && (until == null || createdAt <= until)
}
