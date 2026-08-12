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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * NIP-66 relay monitoring for a client that was going to talk to relays anyway.
 *
 * Construct one, and from then on every connection the client makes is measured
 * ([RelayObserver]), signed and stored as a kind:30166 ([RelayReachabilityStore])
 * on an interval, and folded back into a cheap [isKnownDead] the caller consults
 * when it picks relays. There is nothing else to wire.
 *
 * ## Reading is the cheap side, and it has to be
 *
 * A relay picker runs per event — thousands of times a second in an outbox
 * fan-out — so [isKnownDead] answers from an in-memory snapshot refreshed on the
 * same interval as the writes, never from a store query. The store round trip
 * happens [refreshIntervalMs] apart, not per routing decision.
 *
 * ## The signer is required
 *
 * Measuring relay quality and letting others check it IS NIP-66; a monitor that
 * cannot sign is not a monitor. Making the signer optional would also create the
 * failure this library keeps trying to design out — a component configured,
 * silent, and doing nothing. A client that should not publish simply does not
 * construct one of these, which is a decision visible where it is made.
 *
 * Note that a monitor is its own identity: per NIP-66 it has its own pubkey,
 * profile and relay list, distinct from any user account the client also holds.
 *
 * ## What ends up in the record
 *
 * Only what was observed — connect, read and write round trips, whether the
 * relay actually demanded AUTH, and the network type implied by the url. Nothing
 * is copied out of a relay's NIP-11 document: that is the relay's own claim
 * about itself, available to anyone who asks, and re-publishing it under a
 * monitor's signature would add nothing but an opportunity to go stale.
 */
class RelayMonitor(
    private val client: INostrClient,
    store: IEventStore,
    private val scope: CoroutineScope,
    signer: NostrSigner,
    ttlSeconds: Long = RelayReachabilityStore.DEFAULT_TTL_SECONDS,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
    private val onError: (String) -> Unit = {},
) : AutoCloseable {
    val observer = RelayObserver()

    private val reachability = RelayReachabilityStore(store, signer, ttlSeconds)

    @Volatile private var snapshot: RelayReachabilityStore.Snapshot? = null

    init {
        client.addConnectionListener(observer)
        scope.launch { flushLoop() }
        scope.launch { refreshLoop() }
    }

    /**
     * Skip this relay? Answers from memory, so it is safe to call per routing
     * decision. False until the first [refresh] completes — an unknown relay is
     * one to try, never one to shun.
     */
    fun isKnownDead(relay: NormalizedRelayUrl): Boolean = snapshot?.isKnownDead(relay) == true

    /** Relays proven unreachable within the TTL and not seen live since. */
    fun deadSet(): Set<NormalizedRelayUrl> = snapshot?.dead ?: emptySet()

    /** Relays with a recent successful open, from any monitor whose records we hold. */
    fun liveSet(): Set<NormalizedRelayUrl> = snapshot?.live ?: emptySet()

    /** Re-read the reachability records, including any other monitor's that arrived. */
    suspend fun refresh() {
        runCatching { snapshot = reachability.snapshot() }
            .onFailure { onError("could not read relay reachability: ${it.message}") }
    }

    /**
     * Sign and store what has been observed since the last flush. Returns how
     * many records were written.
     *
     * A relay whose state has not changed is skipped: re-writing its record
     * would refresh a freshness window that nothing re-measured.
     */
    suspend fun flush(): Int {
        val fresh = observer.collectUnreported()
        if (fresh.isEmpty()) return 0
        return runCatching { reachability.record(fresh, TimeUtils.now()) }
            .onFailure { onError("could not write relay reachability: ${it.message}") }
            .getOrDefault(0)
    }

    private suspend fun flushLoop() {
        while (scope.isActive) {
            delay(flushIntervalMs)
            flush()
        }
    }

    private suspend fun refreshLoop() {
        // Immediately, then on the interval: the first thing a run should know is
        // what the last one learned, before it dials anything.
        refresh()
        while (scope.isActive) {
            delay(refreshIntervalMs)
            refresh()
        }
    }

    /**
     * Detach and stop measuring. Does NOT flush — the last write needs a
     * coroutine and a bound on how long a shutdown may block, both of which
     * belong to the caller. Call [flush] inside your own timeout first.
     */
    override fun close() {
        runCatching { client.removeConnectionListener(observer) }
    }

    companion object {
        /**
         * Five minutes: long enough that a flapping relay does not mint a record
         * per flap, short enough that a crash loses little. The records are
         * replaceable, so writing again costs one document, not one more.
         */
        const val DEFAULT_FLUSH_INTERVAL_MS = 5 * 60 * 1000L

        /** How often the in-memory dead/live view is re-read from the store. */
        const val DEFAULT_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }
}
