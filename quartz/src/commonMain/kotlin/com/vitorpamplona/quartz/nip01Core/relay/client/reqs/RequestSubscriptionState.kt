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
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.concurrent.PlatformLock

/**
 * Manages the State of Subscriptions by logging states as the
 * subscription progresses.
 *
 * **Thread-safety: the lock is striped per reference (per relay), not per
 * subscription.** Every operation below is scoped to a single [reference] — all state
 * lives in [RelayState] objects held in a [ConcurrentMap] keyed by it — so two relays
 * delivering EVENTs for the SAME subscription touch disjoint state and no longer
 * serialize on each other.
 *
 * That mattered in production: one subId spans every relay it is subscribed on, so a
 * single per-subscription lock was contended ~191 threads deep on the Pixel 8 that
 * produced `anr_2026-08-03-12-55-26-256` (it was a *spin* lock then, which burned 6 of
 * 9 cores — see [PlatformLock]). Striping removes the contention instead of making
 * waiting cheaper: measured 1.5-2.8x the throughput of one lock per sub in
 * `quartz/src/jvmTest/.../prodbench/LockDesignComparisonBenchmark.kt`. Full analysis,
 * including why a suspending `Mutex` was rejected, is in
 * `quartz/plans/2026-08-03-poolrequests-lock-contention.md`.
 *
 * The stripe array is allocated once and NEVER mutated, so a stripe's identity is
 * stable for this object's whole life. That is load-bearing: if locks lived inside the
 * per-relay values, a thread holding one while another thread dropped and re-created
 * that entry would leave both "inside" the critical section excluding nothing.
 *
 * Callers MUST NOT hold two references' stripes at once ([PoolRequests] locks one relay
 * at a time, including inside its all-subs iterations), and MUST keep socket sends and
 * listener callbacks outside the critical section — they re-enter this class through
 * `onSent`, and the point of a lock is to be held briefly.
 */
class RequestSubscriptionState<T : Any> {
    /**
     * One reference's (relay's) slice of this subscription's state. Plain `var`s: every
     * field is written and read under that reference's stripe by [PoolRequests].
     *
     * Note `RelayActiveRequestStates` uses this class WITHOUT locking. There the fields
     * may be read stale — but the backing [ConcurrentMap] can no longer be structurally
     * corrupted the way the plain `HashMap`s this replaced could.
     */
    private class RelayState {
        /** Null == no REQ state on this relay (fresh, or wiped by connecting/disconnected). */
        var status: ReqSubStatus? = null

        /** Filters of the REQ currently believed to be in flight. */
        var filters: List<Filter>? = null

        /**
         * Survives connect/disconnect so that if new events still arrive we can link
         * them with the filters the relay was processing.
         */
        var lastKnownFilters: List<Filter>? = null

        /**
         * Refused-filter memory. Unlike [status]/[filters] — per-connection wire state
         * wiped by [connecting]/[disconnected] — this SURVIVES reconnects on purpose: a
         * relay that structurally refuses a filter (a search-only relay CLOSING a plain
         * kinds REQ, a relay that "does not accept REQs", "too many filters", …) refuses
         * it again on every new socket, so [PoolRequests.syncState] replaying it each
         * reconnect is pure waste. [refusalCount] accumulates repeated refusals of the
         * same shape so a one-off (transient) close isn't mistaken for a structural one.
         * Cleared on a successful REQ ([onEose]/[onNewEvent]) or when the caller observes
         * the desired filter meaningfully changed.
         */
        var refusedFilters: List<Filter>? = null

        var refusalCount: Int = 0
    }

    private val states = ConcurrentMap<T, RelayState>()

    /**
     * Fixed stripe array — allocated once, never mutated, so lock identity is stable.
     * [STRIPE_COUNT] stripes over ~191 relays is roughly 6-way sharing: a ~32x
     * contention reduction versus one lock per subscription. References colliding on a
     * stripe merely serialize; correctness never depends on N.
     */
    @PublishedApi
    internal val stripes = Array(STRIPE_COUNT) { PlatformLock() }

    @PublishedApi
    internal fun stripeFor(reference: T): PlatformLock = stripes[(reference.hashCode() and 0x7FFFFFFF) % STRIPE_COUNT]

    /**
     * Runs [block] holding [reference]'s stripe. Inline so the per-EVENT hot path
     * allocates no closure.
     */
    inline fun <R> withLock(
        reference: T,
        block: () -> R,
    ): R {
        val lock = stripeFor(reference)
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** Read-only lookup — never creates an entry. */
    private fun peek(reference: T): RelayState? = states[reference]

    /** Write lookup — creates the entry on first use. */
    private fun mutable(reference: T): RelayState = states.getOrPut(reference) { RelayState() }

    fun refusedFilters(reference: T) = peek(reference)?.refusedFilters

    fun refusalCount(reference: T) = peek(reference)?.refusalCount ?: 0

    /**
     * Records that [reference] refused [filters]. [sameAsLastRefusal] must be true when
     * [filters] matches the previously refused shape (the caller owns that comparison,
     * keeping filter-equality policy in one place), so repeated refusals accumulate
     * instead of resetting.
     */
    fun recordRefusal(
        reference: T,
        filters: List<Filter>,
        sameAsLastRefusal: Boolean,
    ) {
        val state = mutable(reference)
        if (sameAsLastRefusal) {
            state.refusalCount += 1
        } else {
            state.refusedFilters = filters
            state.refusalCount = 1
        }
    }

    fun clearRefusal(reference: T) {
        peek(reference)?.let {
            it.refusedFilters = null
            it.refusalCount = 0
        }
    }

    fun currentFilters(reference: T) = peek(reference)?.filters

    fun lastKnownFilterStates(reference: T) = peek(reference)?.lastKnownFilters

    fun currentState(reference: T) = peek(reference)?.status

    fun onNewEvent(reference: T) {
        val state = mutable(reference)
        // The relay is serving this REQ (it matched an event), so any past refusal
        // no longer applies — let it be tried freely again.
        state.refusedFilters = null
        state.refusalCount = 0
        if (state.status == ReqSubStatus.SENT) {
            state.status = ReqSubStatus.QUERYING_PAST
        }
    }

    fun onEose(reference: T) {
        val state = mutable(reference)
        // Reaching EOSE means the relay accepted and finished the REQ; clear any refusal.
        state.refusedFilters = null
        state.refusalCount = 0
        state.status = ReqSubStatus.LIVE
    }

    fun onClosed(reference: T) {
        // Closed messages are usually relays refusing to process a REQ. This keeps
        // [RelayState.filters] intact to avoid sending the same filter, and getting
        // immediately closed, over and over again.
        mutable(reference).status = ReqSubStatus.CLOSED
    }

    fun onOpenReq(
        reference: T,
        filters: List<Filter>,
    ) {
        val state = mutable(reference)
        state.status = ReqSubStatus.SENT
        state.filters = filters
        state.lastKnownFilters = filters
    }

    fun onSubscriptionClosed(reference: T) {
        val state = mutable(reference)
        state.status = ReqSubStatus.CLOSED
        state.filters = null
    }

    fun connecting(reference: T) {
        // Wipes per-connection wire state only; lastKnownFilters and the refusal memory
        // deliberately survive (see [RelayState]).
        peek(reference)?.let {
            it.status = null
            it.filters = null
        }
    }

    fun disconnected(reference: T) {
        peek(reference)?.let {
            it.status = null
            it.filters = null
        }
    }

    companion object {
        /**
         * Comfortably above the IO dispatcher's thread count (64 / 2) so collisions stay
         * rare even when many workers are inside this class at once.
         */
        const val STRIPE_COUNT = 32
    }
}
