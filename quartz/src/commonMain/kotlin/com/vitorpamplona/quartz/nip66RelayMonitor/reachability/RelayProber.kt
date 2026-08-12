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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.networkType
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.requirement
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.rtt
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.NetworkTypeTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RequirementTag
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags.RttType
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * Mass relay census: dials a whole relay universe in parallel waves and returns a
 * per-relay [Verdict] — reachable (with the measured WebSocket-open RTT and, when the
 * relay answered a no-op REQ, its time-to-EOSE) or dead (with the failure reason).
 *
 * The point is to pay the "is this relay alive, and how slow is it?" wait ONCE, up
 * front and concurrently, instead of rediscovering it serially inside a crawl: feed
 * the verdicts to [RelayReachabilityStore.recordProbed] and the next crawl skips the
 * dead set entirely and pre-connects the live set in one storm.
 *
 * Mechanics per wave:
 *  - subscribe a never-matching filter (impossible event id) to every relay in the
 *    wave at once — the client dials them all in parallel (bounded by the transport's
 *    handshake concurrency), a healthy relay EOSEs immediately, and no event payload
 *    is ever streamed;
 *  - a connection listener captures the real WS-upgrade RTT per relay;
 *  - any app-level answer (EOSE, or even a CLOSED — an auth-walled relay is still a
 *    WORKING relay) marks it reachable; a connect failure marks it dead;
 *  - a relay with no terminal by the wave deadline is dead if it never opened the
 *    socket, reachable-but-slow if it did.
 *
 * Wave size should stay at or below the transport's concurrent-handshake cap (and
 * well below the process's file-descriptor budget) — beyond that, extra relays in a
 * wave just queue and eat the wave deadline without dialing.
 */
class RelayProber(
    private val client: INostrClient,
    private val log: (String) -> Unit = {},
) {
    /** One relay's probe outcome. [rttOpenMs]/[rttEoseMs] are -1 when not observed. */
    class Verdict(
        val relay: NormalizedRelayUrl,
        val reachable: Boolean,
        val rttOpenMs: Long,
        val rttEoseMs: Long,
        val error: String?,
    )

    /**
     * One relay's read+write check outcome (see [readWriteCheck]). Latencies are
     * -1 when unobserved; [writeAccepted] is null when the relay never answered
     * the write with an OK (transport failure or silence).
     */
    class ReadWriteVerdict(
        val relay: NormalizedRelayUrl,
        val rttReadMs: Long,
        val rttWriteMs: Long,
        val writeAccepted: Boolean?,
        val writeMessage: String?,
    )

    class Result(
        val verdicts: List<Verdict>,
        val elapsedMs: Long,
    ) {
        val reachable: List<Verdict> get() = verdicts.filter { it.reachable }

        val dead: List<Verdict> get() = verdicts.filter { !it.reachable }

        /** Per-relay open RTT for the reachable set, 0 (= "live, latency unknown") when unobserved. */
        fun reachableRttMs(): Map<NormalizedRelayUrl, Long> = reachable.associate { it.relay to it.rttOpenMs.coerceAtLeast(0) }

        fun deadRelays(): Set<NormalizedRelayUrl> = dead.mapTo(HashSet()) { it.relay }
    }

    /**
     * Probe every relay in [relays], [waveSize] at a time, giving each wave up to
     * [timeoutMs] to reach terminals. Returns one [Verdict] per input relay.
     *
     * [filters] is the REQ each relay is asked to answer. The default
     * [LIVENESS_FILTERS] matches nothing, so an EOSE proves liveness without
     * streaming a payload; pass [readTestFilter] to make [Verdict.rttEoseMs] a
     * real read test instead (the relay must query and stream an actual event).
     */
    suspend fun probe(
        relays: Collection<NormalizedRelayUrl>,
        timeoutMs: Long = 15_000,
        waveSize: Int = 1000,
        filters: List<Filter> = LIVENESS_FILTERS,
    ): Result {
        val mark = TimeSource.Monotonic.markNow()
        val all = ArrayList<Verdict>(relays.size)
        val distinct = relays.toSet()
        var done = 0
        for (wave in distinct.chunked(waveSize.coerceAtLeast(1))) {
            probeWave(wave, timeoutMs, filters) { all += it }
            done += wave.size
            if (distinct.size > wave.size) {
                val liveSoFar = all.count { it.reachable }
                log("[relay-probe] $done/${distinct.size} probed · $liveSoFar reachable")
            }
        }
        return Result(all, mark.elapsedNow().inWholeMilliseconds)
    }

    /**
     * Streaming variant of [probe]: a cold [Flow] that emits each relay's [Verdict]
     * the moment that relay resolves — an answering relay's verdict arrives as soon
     * as its EOSE/CLOSED/connect-failure lands, not when the whole census ends. Only
     * relays that stay silent wait for their wave's [timeoutMs] deadline.
     *
     * [filters] picks the check, as in [probe]: [LIVENESS_FILTERS] (default) or
     * [readTestFilter].
     *
     * Probing starts when the flow is collected, and emission is sequential — a slow
     * collector delays the next wave AND eats into the current wave's [timeoutMs]
     * window (the deadline is absolute; answers keep being recorded while the
     * collector runs, but silent relays get less listening time). Keep per-verdict
     * work light, or buffer, when precise deadlines matter. Pair each verdict with
     * [toDiscoveryEventTemplate] to turn the stream into signable NIP-66 kind:30166
     * records for another process to sign and publish.
     */
    fun probeFlow(
        relays: Collection<NormalizedRelayUrl>,
        timeoutMs: Long = 15_000,
        waveSize: Int = 1000,
        filters: List<Filter> = LIVENESS_FILTERS,
    ): Flow<Verdict> =
        flow {
            for (wave in relays.toSet().chunked(waveSize.coerceAtLeast(1))) {
                probeWave(wave, timeoutMs, filters) { emit(it) }
            }
        }

    /**
     * The deeper, still-honest check pair: READ (a real limit-[readLimit] REQ the
     * relay must query its store for) and WRITE (one ephemeral [RelayProbeWriteTest]
     * event signed by [signer], the monitor key, timed to its OK). Everything is a
     * direct observation — nothing is copied from the relay's NIP-11 self-claims.
     *
     * Run it against relays ALREADY PROVEN LIVE — typically [Result.reachable] of a
     * [probe] that just ran, while the pool's sockets are still open. On a warm
     * socket both numbers are honest NIP-66 rtts (`rtt-read`, `rtt-write`); against
     * a cold relay they silently include the dial, so don't.
     *
     * A write REJECTION is still a measurement: `OK false` proves the write path
     * works and documents policy ([ReadWriteVerdict.writeMessage] keeps the NIP-01
     * machine-readable reason; [toDiscoveryEventTemplate] maps `auth-required:` and
     * `pow:` to `R` tags). Only silence leaves [ReadWriteVerdict.writeAccepted] null.
     */
    suspend fun readWriteCheck(
        relays: Collection<NormalizedRelayUrl>,
        signer: NostrSigner,
        timeoutMs: Long = 15_000,
        waveSize: Int = 1000,
        readLimit: Int = 1,
        readKinds: List<Int>? = listOf(0),
    ): Map<NormalizedRelayUrl, ReadWriteVerdict> {
        val out = HashMap<NormalizedRelayUrl, ReadWriteVerdict>()
        val distinct = relays.toSet()
        for ((waveIndex, wave) in distinct.chunked(waveSize.coerceAtLeast(1)).withIndex()) {
            val reads = HashMap<NormalizedRelayUrl, Long>()
            probeWave(wave, timeoutMs, readTestFilter(readLimit, readKinds)) { reads[it.relay] = it.rttEoseMs }

            // A distinct event id per wave (createdAt has second granularity, so the
            // content must vary) keeps a straggler OK from an earlier wave's relays
            // from ever matching this wave's confirmation window.
            val event = signer.sign(RelayProbeWriteTest.build(content = "NIP-66 write probe $waveIndex"))
            val writes = client.publishAndCollectResults(event, wave.toSet(), (timeoutMs / 1000).coerceAtLeast(1))

            for (relay in wave) {
                // Only a real OK (true or false) counts as an answer; transport
                // failures and silence leave the write side unobserved.
                val answered = writes[relay]?.takeUnless { it.isTransportFailure || it.message == PublishResult.NO_RESPONSE }
                out[relay] =
                    ReadWriteVerdict(
                        relay = relay,
                        rttReadMs = reads[relay] ?: -1,
                        rttWriteMs = answered?.elapsedMs ?: -1,
                        writeAccepted = answered?.accepted,
                        writeMessage = answered?.message,
                    )
            }
        }
        return out
    }

    private suspend fun probeWave(
        wave: List<NormalizedRelayUrl>,
        timeoutMs: Long,
        filters: List<Filter>,
        onVerdict: suspend (Verdict) -> Unit,
    ) {
        val mark = TimeSource.Monotonic.markNow()
        val waveSet = wave.toHashSet()
        val openRtt = ConcurrentMap<NormalizedRelayUrl, Long>()
        val eoseMs = ConcurrentMap<NormalizedRelayUrl, Long>()
        val errors = ConcurrentMap<NormalizedRelayUrl, String>()
        // Every terminal (EOSE / CLOSED / cannot-connect) pings this with its relay so
        // the wait loop can stop as soon as the whole wave has resolved.
        val terminals = Channel<NormalizedRelayUrl>(Channel.UNLIMITED)

        val connListener =
            object : RelayConnectionListener {
                override fun onConnected(
                    relay: IRelayClient,
                    pingMillis: Int,
                    compressed: Boolean,
                ) {
                    if (relay.url in waveSet) openRtt.getOrPut(relay.url) { pingMillis.toLong() }
                }
            }

        val subId = newSubId()
        val subListener =
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // The filter matches nothing; any stray event still proves liveness.
                    eoseMs.getOrPut(relay) { mark.elapsedNow().inWholeMilliseconds }
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    eoseMs.getOrPut(relay) { mark.elapsedNow().inWholeMilliseconds }
                    terminals.trySend(relay)
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // A CLOSED is an app-level ANSWER (auth wall, policy, …): the relay
                    // is working. Record why so the caller can see the wall.
                    errors.getOrPut(relay) { "closed:$message" }
                    terminals.trySend(relay)
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    errors.getOrPut(relay) { "cannot:$message" }
                    terminals.trySend(relay)
                }
            }

        fun verdictOf(relay: NormalizedRelayUrl): Verdict {
            val opened = openRtt[relay]
            val answered = eoseMs[relay]
            val error = errors[relay]
            // Reachable = the socket opened OR the relay answered at the app level
            // (EOSE, or a CLOSED — an auth/policy wall is still a working relay).
            // Only a connect failure, or silence with no socket, is dead.
            val cannot = error?.startsWith("cannot:") == true
            val reachable = !cannot && (opened != null || answered != null || error != null)
            return Verdict(
                relay = relay,
                reachable = reachable,
                rttOpenMs = opened ?: -1,
                rttEoseMs = answered ?: -1,
                error = error,
            )
        }

        client.addConnectionListener(connListener)
        try {
            client.subscribe(subId, wave.associateWith { filters }, subListener)
            val remaining = wave.toMutableSet()
            while (remaining.isNotEmpty()) {
                val left = timeoutMs - mark.elapsedNow().inWholeMilliseconds
                if (left <= 0) break
                val relay = withTimeoutOrNull(left) { terminals.receive() } ?: break
                // The emission happens OUTSIDE the timeout window so a collector that
                // suspends on a verdict can never be cancelled mid-emission and lose it.
                if (remaining.remove(relay)) onVerdict(verdictOf(relay))
            }
            // Whatever is left resolved nothing by the deadline: dead if the socket
            // never opened, reachable-but-slow if it did.
            for (relay in remaining) onVerdict(verdictOf(relay))
        } finally {
            client.unsubscribe(subId)
            client.removeConnectionListener(connListener)
            terminals.close()
        }
    }

    companion object {
        /**
         * A filter no event can match (ids are 64-hex of a hash): the relay answers
         * with an immediate EOSE and never streams a payload. Same trick as the
         * crawler's warm pool. This is the default check — pure liveness.
         */
        val LIVENESS_FILTERS = listOf(Filter(ids = listOf("0".repeat(64))))

        /**
         * A REQ the relay must actually WORK for: query its store and stream up to
         * [limit] real events before the EOSE. Pass to [probe]/[probeFlow] as
         * [filters] to turn [Verdict.rttEoseMs] into a genuine read test rather
         * than a liveness ping — the time still counts from the wave start (dial
         * included), so compare it against [Verdict.rttOpenMs], not across waves.
         *
         * [kinds] defaults to kind 0: purpose relays (purplepag.es) reject any REQ
         * that names no kind with `blocked: filters must specify at least one kind`,
         * and practically every relay stores SOME profile — so a kind-0, limit-1
         * query works everywhere. Pass a different list to probe a specific shelf,
         * or null for a kind-less query on relays known to allow one.
         */
        fun readTestFilter(
            limit: Int = 1,
            kinds: List<Int>? = listOf(0),
        ) = listOf(Filter(kinds = kinds, limit = limit))

        /**
         * The relay universe the local store knows: every read/write relay advertised
         * in any stored kind:10002. Callers typically union this with the reachability
         * cache's live+dead sets so previously-probed relays are re-checked too.
         * `.onion` relays are excluded unless [includeOnion] — without a Tor transport
         * they'd only burn a wave slot. [maxPerAuthority] bounds how many distinct
         * URLs are kept per host[:port]: paid/filter relays mint one path URL per user
         * (`wss://filter.example/npubA`, `/npubB`, …), and probing hundreds of paths
         * of ONE server is redundant (liveness is a server property) and rude.
         */
        suspend fun knownRelayUniverse(
            store: IEventStore,
            includeOnion: Boolean = false,
            maxPerAuthority: Int = 3,
        ): Set<NormalizedRelayUrl> {
            val out = HashSet<NormalizedRelayUrl>()
            val perAuthority = HashMap<String, Int>()
            for (ev in store.query<Event>(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND)))) {
                if (ev !is AdvertisedRelayListEvent) continue
                for (relay in ev.relaysNorm()) {
                    if (!includeOnion && RelayUrlNormalizer.isOnion(relay.url)) continue
                    if (relay in out) continue
                    val authority = authorityOf(relay.url)
                    val count = perAuthority[authority] ?: 0
                    if (count >= maxPerAuthority) continue
                    perAuthority[authority] = count + 1
                    out.add(relay)
                }
            }
            return out
        }

        /** host[:port] between the ws/wss scheme and the first path slash. */
        private fun authorityOf(url: String): String {
            val afterScheme =
                when {
                    url.startsWith("wss://") -> url.substring(6)
                    url.startsWith("ws://") -> url.substring(5)
                    else -> url
                }
            val slash = afterScheme.indexOf('/')
            return if (slash >= 0) afterScheme.substring(0, slash) else afterScheme
        }
    }
}

/**
 * This verdict as an UNSIGNED NIP-66 kind:30166 Relay Discovery template — the d-tag
 * is the normalized relay url; sign it with the consumer's own monitor key (per
 * NIP-66 a monitor is its own identity, so the prober never signs on its own).
 *
 * Only facts a probe actually observed are tagged:
 *  - `n` network type inferred from the url (clearnet/tor/i2p);
 *  - `rtt-open` when the relay was reachable — the measured WS-upgrade round trip,
 *    or 0 for "reachable, latency not observed" (liveness is the tag's PRESENCE);
 *  - `rtt-read`/`rtt-write` when a [RelayProber.readWriteCheck] result is passed
 *    as [readWrite] and actually measured that side;
 *  - `R auth` when the relay answered a probe with a NIP-42 `auth-required`
 *    (CLOSED on the REQ, or OK-false on the write) and `R pow` when the write
 *    was refused with a `pow:` reason — observed walls, not NIP-11 claims.
 *
 * NIP-11-derived tags (`N` supported NIPs, `k` kinds, `T` type) are deliberately
 * absent: those are the relay's self-claims, and asserting them under a monitor
 * signature without a per-NIP compliance test would launder claims into
 * measurements. [Verdict.rttEoseMs] is likewise never written as `rtt-read` — it
 * is wave-relative (dial + TLS + queueing), so the honest read number only comes
 * from [readWrite] (or the [RelayObserver]/[RelayMonitor] real-traffic path).
 */
fun RelayProber.Verdict.toDiscoveryEventTemplate(
    createdAt: Long = TimeUtils.now(),
    readWrite: RelayProber.ReadWriteVerdict? = null,
    current: RelayDiscoveryEvent? = null,
): EventTemplate<RelayDiscoveryEvent> =
    RelayDiscoveryEvent.build(
        relay,
        current?.content ?: "",
        // Strictly newer than what it replaces, or a store enforcing
        // replaceable semantics rejects it and the probe is lost silently.
        createdAt = maxOf(createdAt, (current?.createdAt ?: 0L) + 1),
    ) {
        current?.tags?.forEach { tag ->
            if (tag.firstOrNull() != "d" && !probeOwns(tag, readWrite != null)) add(tag)
        }
        networkType(RelayReachabilityStore.networkTypeOf(relay))
        if (reachable) rtt(RttType.OPEN, rttOpenMs.coerceAtLeast(0))
        if (readWrite != null) {
            if (readWrite.rttReadMs >= 0) rtt(RttType.READ, readWrite.rttReadMs)
            if (readWrite.rttWriteMs >= 0) rtt(RttType.WRITE, readWrite.rttWriteMs)
        }
        val authWalled =
            error?.startsWith("closed:auth-required") == true ||
                readWrite?.writeMessage?.startsWith("auth-required") == true
        if (authWalled) requirement(RelayReachabilityStore.AUTH_REQUIREMENT)
        if (readWrite?.writeMessage?.startsWith("pow:") == true) requirement(POW_REQUIREMENT)
    }

/** The NIP-66 requirement a write probe can prove, alongside `auth`. */
private const val POW_REQUIREMENT = "pow"

/**
 * What a probe verdict measured, and may therefore replace in [current].
 *
 * A 30166 carries ONE `created_at`, so any tag carried across is re-dated as a
 * current measurement — this verdict's own facts must be rewritten wholesale or
 * a stale latency is republished as today's number.
 *
 * [hasReadWrite] narrows it: without a [RelayProber.ReadWriteVerdict] this probe
 * never exercised the write path, so `R pow` is somebody else's finding and is
 * carried across rather than deleted. Both polarities of each requirement are
 * owned, so an update cannot leave the record asserting `pow` and `!pow` at once.
 */
private fun probeOwns(
    tag: Array<String>,
    hasReadWrite: Boolean,
): Boolean =
    when (tag.firstOrNull()) {
        NetworkTypeTag.TAG_NAME -> true
        RttType.OPEN.tagName, RttType.READ.tagName, RttType.WRITE.tagName -> true
        RequirementTag.TAG_NAME ->
            when (tag.getOrNull(1)) {
                RelayReachabilityStore.AUTH_REQUIREMENT, "!" + RelayReachabilityStore.AUTH_REQUIREMENT -> true
                POW_REQUIREMENT, "!" + POW_REQUIREMENT -> hasReadWrite
                else -> false
            }
        else -> false
    }
