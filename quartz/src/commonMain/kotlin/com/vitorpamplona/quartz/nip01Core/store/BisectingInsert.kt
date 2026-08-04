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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.CancellationException

/**
 * Write [events] through [write]; if that throws, split the batch and write
 * the halves, down to the single event the writer cannot take.
 *
 * A bulk write like [IEventStore.batchInsert] fails as a unit, so one bad
 * event would otherwise cost the whole batch — 999 good events lost per bad
 * one at a 1000-event batch, with no retry. Bisecting costs ~2·log2(n) extra
 * writes on a failing batch, nothing on a healthy one, and ends holding the
 * offender by itself for [onPoison] to report. Re-writing the good halves is
 * safe: re-inserting an already-applied event is a duplicate the store
 * rejects.
 *
 * Splitting assumes ONE event is at fault. When the store itself is refusing
 * (a full disk, a dead engine) every half fails all the way down and
 * isolation would turn one failed write into ~2n — precisely the wrong moment
 * to multiply the load. So isolation spends a fixed [budget] of writes and
 * hands the remainder to [onGaveUp]: "we could not say which" is a different
 * fact from "this event is bad", and a caller should count them apart.
 */
suspend fun insertBisecting(
    events: List<Event>,
    write: suspend (List<Event>) -> List<IEventStore.InsertOutcome>,
    onOutcomes: (List<IEventStore.InsertOutcome>) -> Unit,
    onPoison: (Event, Throwable) -> Unit,
    onGaveUp: (List<Event>, Throwable) -> Unit = { _, _ -> },
    budget: Int = ISOLATION_WRITE_BUDGET,
) = bisect(events, write, onOutcomes, onPoison, onGaveUp, intArrayOf(budget))

private suspend fun bisect(
    events: List<Event>,
    write: suspend (List<Event>) -> List<IEventStore.InsertOutcome>,
    onOutcomes: (List<IEventStore.InsertOutcome>) -> Unit,
    onPoison: (Event, Throwable) -> Unit,
    onGaveUp: (List<Event>, Throwable) -> Unit,
    budget: IntArray,
) {
    if (events.isEmpty()) return
    try {
        onOutcomes(write(events))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (events.size == 1) {
            onPoison(events.single(), e)
            return
        }
        if (budget[0] <= 0) {
            onGaveUp(events, e)
            return
        }
        budget[0] -= 2
        val mid = events.size / 2
        bisect(events.subList(0, mid), write, onOutcomes, onPoison, onGaveUp, budget)
        bisect(events.subList(mid, events.size), write, onOutcomes, onPoison, onGaveUp, budget)
    }
}

/**
 * Writes one batch may spend isolating its bad events before giving up.
 * Isolating k bad events out of n costs about `2·k·log2(n)` writes, so 64
 * covers three in a 1000-event batch — past the rate seen in practice. What
 * it really bounds is the store-wide case, where every write fails.
 */
const val ISOLATION_WRITE_BUDGET = 64
