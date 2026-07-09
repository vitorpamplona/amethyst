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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.networkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.rtt
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RttType
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A durable, shareable relay-reachability cache backed by an [IEventStore] as
 * NIP-66 **kind:30166 Relay Discovery** events — so the crawler, the WoT updater,
 * and future runs all read and write the *same* liveness knowledge instead of each
 * rediscovering dead relays from an in-memory set that is wiped when the process ends.
 *
 * ## Why NIP-66 / the event store
 * A 30166 event is addressable by its `d`-tag (the normalized relay URL), so the
 * store keeps exactly **one replaceable record per (monitor, relay)** — a natural
 * per-relay status slot with a `created_at` timestamp that gives us a free TTL. The
 * event store gives us persistence, cross-procedure sharing, and interop for free:
 * 30166 events published by *other* monitors (nostr.watch et al.) can be ingested to
 * seed reachability without probing, and our own records can be published back.
 *
 * ## How "dead" is represented
 * NIP-66 has no explicit offline field; liveness is inferred from a fresh record that
 * carries an `rtt-open` (a successful connection). This cache follows that convention:
 *  - **reachable** → a 30166 **with** `rtt-open`, `created_at` = probe time.
 *  - **dead** → a 30166 **without** `rtt-open` ("we checked, could not open"),
 *    `created_at` = probe time.
 *
 * So a fresh rtt-less record distinguishes *checked-and-dead* from *never-checked*
 * (no record). When both a dead and a live record exist within the TTL for the same
 * relay, **live wins** — any recent successful open overrides an earlier failure,
 * whether the two came from us across time or from two different monitors.
 *
 * ## Not a replacement for the hot path
 * [snapshot] is meant to be loaded ONCE at the start of a run into whatever in-memory
 * structure the caller already uses for per-request `isDead` checks; [record] flushes
 * a run's findings back at the end. It is deliberately not queried per routing decision.
 *
 * A relay is only ever skipped for the TTL window, never permanently — consistent with
 * the outbox rule that every advertised write relay must be tried: a TTL'd record is
 * "skip for now", not "ignore this author's home forever".
 *
 * ## The signer is a dedicated monitor service identity
 * [signer] should be a **machine-level monitor key**, NOT a user/observer account: per
 * NIP-66 a monitor is its own pubkey (which also publishes a kind:10166 announcement,
 * a kind:0 profile and a kind:10002). Publishing these under the observer's key would
 * conflate the WoT identity with a relay-monitoring service. [snapshot] still honours
 * records from ANY author (so third-party monitors can be ingested); only [record]
 * writes under this monitor key.
 */
class RelayReachabilityStore(
    private val store: IEventStore,
    private val signer: NostrSigner,
    private val ttlSeconds: Long = DEFAULT_TTL_SECONDS,
) {
    /**
     * An in-memory view of the fresh (within-TTL) reachability records. [dead] holds
     * relays proven unreachable and not since seen live; [live] holds relays with a
     * recent successful open. A relay absent from both is simply unknown — re-probe it.
     */
    class Snapshot(
        val dead: Set<NormalizedRelayUrl>,
        val live: Set<NormalizedRelayUrl>,
    ) {
        fun isKnownDead(relay: NormalizedRelayUrl) = relay in dead

        val size: Int get() = dead.size + live.size
    }

    /**
     * Load every 30166 record fresher than [ttlSeconds] and fold it into a [Snapshot].
     * Records from any monitor are honoured (live-wins), so ingesting third-party
     * monitors' 30166 into [store] transparently improves the result.
     */
    suspend fun snapshot(now: Long = TimeUtils.now()): Snapshot {
        val since = now - ttlSeconds
        val events =
            store.query<RelayDiscoveryEvent>(
                Filter(kinds = listOf(RelayDiscoveryEvent.KIND), since = since),
            )
        val live = HashSet<NormalizedRelayUrl>()
        val dead = HashSet<NormalizedRelayUrl>()
        for (ev in events) {
            val relay = ev.relay() ?: continue
            if (ev.rttOpen() != null) live.add(relay) else dead.add(relay)
        }
        // A recent successful open (from us later, or from another monitor) overrides
        // an earlier dead mark for the same relay.
        dead.removeAll(live)
        return Snapshot(dead, live)
    }

    /**
     * Persist a run's reachability findings as 30166 events: each [reachable] relay as
     * a record WITH `rtt-open`, each [dead] relay (that is not also reachable) as one
     * WITHOUT. Signed by [signer] and inserted into [store]; being addressable, each
     * replaces this monitor's prior record for that relay, so the store stays bounded
     * at roughly the number of distinct relays.
     */
    suspend fun record(
        reachable: Set<NormalizedRelayUrl>,
        dead: Set<NormalizedRelayUrl>,
        now: Long = TimeUtils.now(),
        rttOpenMs: Long = 0,
    ) {
        for (relay in reachable) writeOne(relay, up = true, now, rttOpenMs)
        for (relay in dead) if (relay !in reachable) writeOne(relay, up = false, now, rttOpenMs)
    }

    private suspend fun writeOne(
        relay: NormalizedRelayUrl,
        up: Boolean,
        now: Long,
        rttOpenMs: Long,
    ) {
        val template =
            RelayDiscoveryEvent.build(relay, createdAt = now) {
                networkType(networkTypeOf(relay))
                if (up) rtt(RttType.OPEN, rttOpenMs)
            }
        store.insert(signer.sign(template))
    }

    companion object {
        /** Default freshness window: a relay's status is trusted for a day, then re-probed. */
        const val DEFAULT_TTL_SECONDS = 24L * 60 * 60

        /** NIP-66 `n` network type inferred from the URL, so a `.onion`/i2p relay is tagged correctly. */
        fun networkTypeOf(relay: NormalizedRelayUrl): NetworkType =
            when {
                relay.url.contains(".onion") -> NetworkType.TOR
                relay.url.contains(".i2p") -> NetworkType.I2P
                else -> NetworkType.CLEARNET
            }
    }
}
