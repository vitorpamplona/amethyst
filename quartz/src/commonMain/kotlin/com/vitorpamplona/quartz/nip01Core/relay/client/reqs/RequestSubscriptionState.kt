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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Manages the State of Subscriptions by logging states as the
 * subscription progresses.
 *
 * Thread-safety: the plain maps below are only touched inside [withLock].
 * The lock lives HERE — one per subscription — instead of a single global
 * lock in PoolRequests, because every mutation is scoped to one subId:
 * relays delivering EVENTs for *different* subscriptions have no shared
 * state and must not serialize on each other. (A single global spin lock
 * measured *negative* scaling: 4 relay consumer threads pushed less
 * aggregate throughput through it than 1 — see
 * quartz/plans/2026-07-02-nostrclient-receiver-perf.md.)
 */
@OptIn(ExperimentalAtomicApi::class)
class RequestSubscriptionState<T> {
    /**
     * Tiny non-reentrant spin lock (same primitive as BasicRelayClient's
     * connecting mutex). Critical sections are a handful of map operations,
     * never I/O — callers MUST NOT re-enter and MUST NOT hold two
     * subscriptions' locks at once (PoolRequests locks one sub at a time,
     * including inside its all-subs iterations).
     *
     * `@PublishedApi internal` only because [withLock] is inline (this sits
     * on the per-EVENT hot path; inlining avoids a closure allocation per
     * message) — treat it as private.
     */
    @PublishedApi
    internal val lock = AtomicBoolean(false)

    inline fun <R> withLock(block: () -> R): R {
        while (lock.exchange(true)) {
            // Test-and-test-and-set: spin-read until it looks free (cheaper
            // on the cache line than hammering exchange), then retry above.
            while (lock.load()) { }
        }
        try {
            return block()
        } finally {
            lock.store(false)
        }
    }

    // Logs the state of each channel to:
    // 1. inform when an event is received as live
    // 2. to block REQs being sent before finished (receiving an EOSE or Closed)
    //
    // If 2 happens, the relay might send multiple EOSEs in sequence
    // for the same sub and we won't know which REQ was it for.
    private val subStates = mutableMapOf<T, ReqSubStatus>()
    private val filterStates = mutableMapOf<T, List<Filter>>()

    /**
     * This cache is used to make sure we know what the relay was processing
     * before a close or disconnect so that if new events still arrive
     * we can link them with the appropriate filters.
     */
    private val lastKnownFilterStates = mutableMapOf<T, List<Filter>>()

    fun currentFilters() = filterStates

    fun currentFilters(reference: T) = filterStates[reference]

    fun lastKnownFilterStates(reference: T) = lastKnownFilterStates[reference]

    fun currentState(reference: T) = subStates[reference]

    fun onNewEvent(reference: T) {
        if (subStates[reference] == ReqSubStatus.SENT) {
            subStates[reference] = ReqSubStatus.QUERYING_PAST
        }
    }

    fun onEose(reference: T) {
        subStates[reference] = ReqSubStatus.LIVE
    }

    fun onClosed(reference: T) {
        subStates[reference] = ReqSubStatus.CLOSED
        // Closed messages are usually relays refusing to process a REQ
        // This message keeps the state of filterStates intact to
        // avoid sending the same filter, and getting immediately closed,
        // over and over again.

        // filterStates.remove(reference)
    }

    fun onOpenReq(
        reference: T,
        filters: List<Filter>,
    ) {
        subStates[reference] = ReqSubStatus.SENT
        filterStates[reference] = filters
        lastKnownFilterStates[reference] = filters
    }

    fun onSubscriptionClosed(reference: T) {
        subStates[reference] = ReqSubStatus.CLOSED
        filterStates.remove(reference)
    }

    fun connecting(reference: T) {
        subStates.remove(reference)
        filterStates.remove(reference)
    }

    fun disconnected(reference: T) {
        subStates.remove(reference)
        filterStates.remove(reference)
    }
}
