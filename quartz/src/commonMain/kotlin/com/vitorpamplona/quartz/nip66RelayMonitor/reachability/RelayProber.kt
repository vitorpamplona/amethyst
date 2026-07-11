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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.channels.Channel
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
     */
    suspend fun probe(
        relays: Collection<NormalizedRelayUrl>,
        timeoutMs: Long = 15_000,
        waveSize: Int = 1000,
    ): Result {
        val mark = TimeSource.Monotonic.markNow()
        val all = ArrayList<Verdict>(relays.size)
        val distinct = relays.toSet()
        var done = 0
        for (wave in distinct.chunked(waveSize.coerceAtLeast(1))) {
            all += probeWave(wave, timeoutMs)
            done += wave.size
            if (distinct.size > wave.size) {
                val liveSoFar = all.count { it.reachable }
                log("[relay-probe] $done/${distinct.size} probed · $liveSoFar reachable")
            }
        }
        return Result(all, mark.elapsedNow().inWholeMilliseconds)
    }

    private suspend fun probeWave(
        wave: List<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): List<Verdict> {
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
                override fun onEvent(
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

        client.addConnectionListener(connListener)
        try {
            client.subscribe(subId, wave.associateWith { PROBE_FILTERS }, subListener)
            val remaining = wave.toMutableSet()
            withTimeoutOrNull(timeoutMs) {
                while (remaining.isNotEmpty()) {
                    remaining.remove(terminals.receive())
                }
            }
        } finally {
            client.unsubscribe(subId)
            client.removeConnectionListener(connListener)
            terminals.close()
        }

        return wave.map { relay ->
            val opened = openRtt[relay]
            val answered = eoseMs[relay]
            val error = errors[relay]
            // Reachable = the socket opened OR the relay answered at the app level
            // (EOSE, or a CLOSED — an auth/policy wall is still a working relay).
            // Only a connect failure, or silence with no socket, is dead.
            val cannot = error?.startsWith("cannot:") == true
            val reachable = !cannot && (opened != null || answered != null || error != null)
            Verdict(
                relay = relay,
                reachable = reachable,
                rttOpenMs = opened ?: -1,
                rttEoseMs = answered ?: -1,
                error = error,
            )
        }
    }

    companion object {
        // A filter no event can match (ids are 64-hex of a hash): the relay answers
        // with an immediate EOSE and never streams a payload. Same trick as the
        // crawler's warm pool.
        private val PROBE_FILTERS = listOf(Filter(ids = listOf("0".repeat(64))))

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
