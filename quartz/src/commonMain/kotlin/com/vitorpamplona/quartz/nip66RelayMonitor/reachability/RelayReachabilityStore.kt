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

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.networkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.requirement
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.rtt
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkTypeTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RequirementTag
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
     *
     * [rttOpenMs] is the measured open round-trip in ms. It defaults to 0 as a **liveness
     * flag only** — presence of the `rtt-open` tag, not its magnitude, is what [snapshot]
     * reads as "reachable", and a caller that merely proved a relay served events (like
     * the crawler) has no dedicated probe latency to report. A `0` therefore means
     * "reachable, latency not probed by this writer", NOT a real 0 ms measurement. Do NOT
     * publish these records to the wider network as authoritative latency data until a
     * dedicated monitor probe supplies a real [rttOpenMs]; aggregators rank by it.
     */
    suspend fun record(
        reachable: Set<NormalizedRelayUrl>,
        dead: Set<NormalizedRelayUrl>,
        now: Long = TimeUtils.now(),
        rttOpenMs: Long = 0,
    ) {
        val current = currentRecords(reachable + dead)
        for (relay in reachable) writeSafely { writeOne(relay, up = true, now, rttOpenMs, current[relay]) }
        for (relay in dead) if (relay !in reachable) writeSafely { writeOne(relay, up = false, now, rttOpenMs, current[relay]) }
    }

    /**
     * Like [record], but with a real, per-relay measured open round-trip — the shape a
     * dedicated probe (see RelayProber) produces. Each reachable relay's record carries
     * ITS OWN `rtt-open`, so the cache doubles as a latency census: a later reader can
     * separate fast relays from working-but-slow ones instead of only live from dead.
     */
    suspend fun recordProbed(
        reachableRttMs: Map<NormalizedRelayUrl, Long>,
        dead: Set<NormalizedRelayUrl>,
        now: Long = TimeUtils.now(),
    ) {
        val current = currentRecords(reachableRttMs.keys + dead)
        for ((relay, rtt) in reachableRttMs) writeSafely { writeOne(relay, up = true, now, rtt.coerceAtLeast(0), current[relay]) }
        for (relay in dead) if (relay !in reachableRttMs) writeSafely { writeOne(relay, up = false, now, 0, current[relay]) }
    }

    /**
     * Write everything a run observed, one replaceable record per relay.
     *
     * Skips a relay it learned nothing about — one that was never dialled, or
     * only started connecting. Silence is not evidence, and a record written on
     * no observation would refresh a freshness window nothing re-measured.
     *
     * Returns the number of records written.
     */
    suspend fun record(
        observations: Collection<RelayObserver.Observation>,
        now: Long = TimeUtils.now(),
    ): Int {
        val reported = observations.filter { it.reachable || it.error != null }
        val current = currentRecords(reported.map { it.url })
        var written = 0
        for (o in reported) {
            if (writeSafely { writeObserved(o, now, current[o.url]) }) written++
        }
        return written
    }

    /**
     * Run one relay's write, keeping its failure to that relay.
     *
     * The read-modify-write below spans a store round trip and [IEventStore]
     * offers no read inside a transaction, so a concurrent writer to the same
     * address can still win the race — and on a store enforcing replaceable
     * semantics our now-stale insert is REJECTED. Unisolated, that one throw
     * ends the loop and drops every relay after it; the run reports fewer
     * records than it measured and nothing says why.
     */
    private inline fun writeSafely(write: () -> Unit): Boolean =
        try {
            write()
            true
        } catch (e: Exception) {
            false
        }

    private suspend fun writeObserved(
        o: RelayObserver.Observation,
        now: Long,
        current: RelayDiscoveryEvent?,
    ) {
        // Owns the `auth` REQUIREMENT VALUE, not the whole `R` tag name: an
        // observation learns whether this relay challenged us and may clear
        // that, but `R payment`, `R pow` and the negated forms — written by
        // RelayProber among others — are somebody else's measurement.
        val template =
            edit(o.url, now, current, { tag ->
                tag.firstOrNull() == NetworkTypeTag.TAG_NAME ||
                    tag.firstOrNull() in ALL_RTT ||
                    (tag.firstOrNull() == RequirementTag.TAG_NAME && tag.getOrNull(1) == AUTH_REQUIREMENT)
            }) {
                networkType(networkTypeOf(o.url))
                if (o.reachable) {
                    // Liveness is the presence of rtt-open, per NIP-66. A relay we
                    // reached without timing the open — served us an event on a
                    // socket that was already up — still gets the tag so it reads
                    // as live, but never an invented latency: 0 would be a lie
                    // aggregators rank on.
                    o.rttOpenMs?.let { rtt(RttType.OPEN, it) } ?: rtt(RttType.OPEN, 0)
                    o.rttReadMs?.let { rtt(RttType.READ, it) }
                    o.rttWriteMs?.let { rtt(RttType.WRITE, it) }
                }
                // Observed, not read off NIP-11: this relay actually challenged
                // us. A relay advertising open reads and then demanding AUTH is
                // exactly what a monitor exists to catch.
                if (o.authRequired) requirement(AUTH_REQUIREMENT)
            }
        store.insert(signer.sign(template))
    }

    private suspend fun writeOne(
        relay: NormalizedRelayUrl,
        up: Boolean,
        now: Long,
        rttOpenMs: Long,
        current: RelayDiscoveryEvent?,
    ) {
        // Owns every rtt only when writing a DEAD record: liveness is the
        // presence of rtt-open, so a relay that went down must lose all of
        // them. On the reachable path it owns rtt-open alone — deleting a
        // `rtt-read` this call never measured is the same silent loss this
        // whole change exists to stop.
        val owned =
            if (up) {
                { tag: Array<String> -> tag.firstOrNull() == NetworkTypeTag.TAG_NAME || tag.firstOrNull() == RttType.OPEN.tagName }
            } else {
                { tag: Array<String> -> tag.firstOrNull() == NetworkTypeTag.TAG_NAME || tag.firstOrNull() in ALL_RTT }
            }
        val template =
            edit(relay, now, current, owned) {
                networkType(networkTypeOf(relay))
                if (up) rtt(RttType.OPEN, rttOpenMs)
            }
        store.insert(signer.sign(template))
    }

    /**
     * Build this monitor's next record for [relay] as an EDIT of [current]
     * rather than a fresh document.
     *
     * A 30166 is addressable, so a relay has exactly one record per monitor —
     * and this class is not necessarily its only writer. Anything else keeping
     * per-relay knowledge under the same identity (an operator marking a relay
     * as a mirror of another, a crawler recording which kinds it served) writes
     * into this same slot, and a build-from-scratch silently deletes it. The
     * result still signs, still parses, and still reads as a valid NIP-66
     * record — it just says less than it did, and the reader downstream has no
     * way to know something was lost.
     *
     * [owns] decides what this writer measured and may therefore replace — a
     * predicate rather than a set of names, because ownership is sometimes per
     * VALUE: an observation may clear `R auth` without touching the `R pow`
     * another writer measured. Everything it does not claim is carried across
     * untouched, including tags this version of quartz has never heard of.
     *
     * The timestamp is `max(now, current + 1)`, not `now`: a store enforcing
     * replaceable semantics REJECTS a record that is not strictly newer than
     * the one it replaces, and two writers inside the same second — or a peer
     * whose clock runs slightly ahead — are ordinary. An update lost that way
     * is indistinguishable from one that had nothing to say.
     *
     * That bump is CAPPED at [MAX_FUTURE_SKEW_SECONDS] past `now`. Without a
     * ceiling a `created_at` that once landed in the future is sticky: every
     * later edit derives from the bad value and never re-anchors, so the record
     * never ages out of [snapshot]'s TTL window (a stale `isKnownDead` that can
     * never expire) and relays enforcing future-timestamp limits reject
     * everything this monitor publishes for that relay. Capped, a pathological
     * record costs the updates made while `now` catches up — bounded, and it
     * heals itself — instead of poisoning the slot permanently.
     */
    private fun edit(
        relay: NormalizedRelayUrl,
        now: Long,
        current: RelayDiscoveryEvent?,
        owns: (Array<String>) -> Boolean,
        measured: TagArrayBuilder<RelayDiscoveryEvent>.() -> Unit,
    ) = RelayDiscoveryEvent.build(
        relay,
        current?.content ?: "",
        createdAt = minOf(maxOf(now, (current?.createdAt ?: 0L) + 1), now + MAX_FUTURE_SKEW_SECONDS),
    ) {
        current?.tags?.forEach { tag ->
            if (tag.firstOrNull() != "d" && !owns(tag)) add(tag)
        }
        measured()
    }

    /**
     * This monitor's own current record for each relay, in one query.
     *
     * Only OUR records: merging another monitor's tags into a document signed
     * with this key would republish their claims as ours.
     */
    private suspend fun currentRecords(relays: Collection<NormalizedRelayUrl>): Map<NormalizedRelayUrl, RelayDiscoveryEvent> {
        if (relays.isEmpty()) return emptyMap()
        val out = HashMap<NormalizedRelayUrl, RelayDiscoveryEvent>()
        // CHUNKED: a `d` filter binds one host parameter per url, and callers
        // pass the whole relay universe — RelayProber's own measurement puts
        // that at 16,507. A bundled SQLite refuses past 32,766 variables, and
        // the throw would land BEFORE anything was written, losing an entire
        // probe run's records rather than one relay's.
        for (chunk in relays.map { it.url }.distinct().chunked(RELAYS_PER_QUERY)) {
            val held =
                store.query<RelayDiscoveryEvent>(
                    Filter(kinds = listOf(RelayDiscoveryEvent.KIND), authors = listOf(signer.pubKey), tags = mapOf("d" to chunk)),
                )
            for (ev in held) {
                val relay = ev.relay() ?: continue
                val seen = out[relay]
                if (seen == null || ev.createdAt > seen.createdAt) out[relay] = ev
            }
        }
        return out
    }

    companion object {
        /** Every rtt tag name. A DEAD record must clear all of them: liveness is the presence of `rtt-open`. */
        private val ALL_RTT = setOf(RttType.OPEN.tagName, RttType.READ.tagName, RttType.WRITE.tagName)

        /** The one NIP-66 requirement an observation can prove: the relay challenged us. */
        const val AUTH_REQUIREMENT = "auth"

        /**
         * How far past `now` an edit may stamp itself to clear a record that
         * is already ahead of the clock. Enough to cover ordinary skew between
         * two writers; small enough that a pathological record heals in
         * minutes rather than never. See [edit].
         */
        const val MAX_FUTURE_SKEW_SECONDS = 60L

        /**
         * Urls per `d` lookup. Well under a bundled SQLite's 32,766-variable
         * ceiling, and in the same range as the author chunking elsewhere in
         * this codebase.
         */
        const val RELAYS_PER_QUERY = 500

        /** Default freshness window: a relay's status is trusted for a day, then re-probed. */
        const val DEFAULT_TTL_SECONDS = 24L * 60 * 60

        /** NIP-66 `n` network type inferred from the URL, so a `.onion`/i2p relay is tagged correctly. */
        fun networkTypeOf(relay: NormalizedRelayUrl): NetworkType =
            when {
                RelayUrlNormalizer.isOnion(relay.url) -> NetworkType.TOR
                relay.url.contains(".i2p") -> NetworkType.I2P
                else -> NetworkType.CLEARNET
            }
    }
}
