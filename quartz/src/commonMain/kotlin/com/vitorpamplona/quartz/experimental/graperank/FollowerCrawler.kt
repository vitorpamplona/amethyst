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
package com.vitorpamplona.quartz.experimental.graperank

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.ParallelEventVerifier
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * The reverse of [GrapeRankCrawler]: finds every user who **follows** the observer
 * by asking as many relays as possible for kind:3 contact lists that `#p`-tag the
 * observer, and persists each one to [store]. The outbox model can't find these —
 * you don't know a follower until you've seen their list, so you can't route to
 * their outbox first — so this casts a wide net across the whole relay universe
 * ([Config.relays], assembled by the caller from the reachability cache, the
 * kind:10002 relays in the store, and the index/aggregator relays that hold
 * reverse-follow data for the network).
 *
 * Each discovered kind:3 is a follower's full contact list, so persisting it also
 * enriches the graph a later `graperank score` builds from the store — the
 * follower becomes a FOLLOW edge into the observer (and into everyone else it
 * follows). A popular observer can have far more followers than a relay's per-REQ
 * cap, so every relay is paged on its own `created_at` cursor via
 * [fetchAllPagesFromPool].
 *
 * Transport-agnostic within quartz: it takes an [INostrClient] and an
 * [IEventStore]; relay *policy* (which relays make up "all possible relays") is the
 * caller's, injected through [Config]. Progress is emitted through [log].
 */
class FollowerCrawler(
    private val client: INostrClient,
    private val store: IEventStore,
    private val config: Config,
    private val log: (String) -> Unit = {},
) {
    /**
     * @param relays the relay universe to query — "all possible relays" is the
     *   caller's policy (reachability-cache live set + every kind:10002 relay in the
     *   store + the index/aggregator relays). Empty means nothing to do.
     * @param maxPerRelay TOTAL cap on followers pulled from each relay, or `null`
     *   (default) to pull **every** follower a relay holds. This is NOT a page size:
     *   [fetchAllPages] treats a filter's `limit` as the total across all pages and
     *   stops paging once it's reached, so a non-null value cuts the crawl short at
     *   that many per relay. Leave it null for completeness; set it only to bound a
     *   spot check. The per-page size is the relay's own default either way.
     * @param timeoutMs per-page EOSE timeout for a relay before its next page fires.
     * @param maxConcurrentRelays how many relays page at once (a global fan-out cap).
     * @param insertBatchSize verified events group-committed per [IEventStore.batchInsert].
     */
    class Config(
        val relays: Set<NormalizedRelayUrl>,
        val maxPerRelay: Int? = null,
        val timeoutMs: Long = 15_000,
        val maxConcurrentRelays: Int = 16,
        val insertBatchSize: Int = 500,
    )

    /** What the follower crawl found. */
    class Stats(
        val relaysQueried: Int,
        /** Relays that returned at least one valid follower list. */
        val relaysAnswered: Int,
        /** Distinct followers (kind:3 authors that `#p`-tag the observer). */
        val followersFound: Int,
        /** Verified kind:3 events handed to the store (superseded versions included). */
        val eventsStored: Long,
        val downloadMs: Long,
    )

    /**
     * Find and persist every follower of [observer] across [Config.relays]. Verifies
     * each event (a relay can lie), keeps only kind:3 that actually `#p`-tag the
     * observer (a relay can over-return), dedups by event id, and group-commits to
     * the store. Returns [Stats].
     */
    suspend fun crawl(observer: HexKey): Stats =
        withContext(Dispatchers.IO) {
            if (config.relays.isEmpty()) return@withContext Stats(0, 0, 0, 0L, 0L)

            val mark = TimeSource.Monotonic.markNow()
            // No limit by default → fetchAllPages walks the whole result set page by
            // page; a non-null maxPerRelay caps the total per relay (see Config).
            val filter = Filter(kinds = FOLLOW_KINDS, tags = mapOf("p" to listOf(observer)), limit = config.maxPerRelay)
            val perRelay = config.relays.associateWith { listOf(filter) }

            // These three are read/written ONLY by the verifier's single drain
            // coroutine (ParallelEventVerifier dispatches onVerified in order from one
            // coroutine), so plain collections are safe — no concurrent access.
            val followers = HashSet<HexKey>()
            val seen = HashSet<HexKey>()
            val answered = HashSet<NormalizedRelayUrl>()
            var eventsStored = 0L

            // Verified follower lists cross to a single persister coroutine that can
            // suspend on the store; onVerified itself must not (it's a plain callback).
            val toPersist = Channel<Event>(Channel.UNLIMITED)

            coroutineScope {
                val persister =
                    launch(Dispatchers.IO) {
                        val flushAt = config.insertBatchSize.coerceAtLeast(1)
                        val buffer = ArrayList<Event>(flushAt)

                        suspend fun flush() {
                            if (buffer.isEmpty()) return
                            store.batchInsert(buffer)
                            eventsStored += buffer.size
                            buffer.clear()
                        }
                        for (event in toPersist) {
                            buffer.add(event)
                            if (buffer.size >= flushAt) flush()
                        }
                        flush()
                    }

                val verifier =
                    ParallelEventVerifier<NormalizedRelayUrl>(
                        scope = this,
                        onVerified = { event, relay ->
                            // A relay can over-return; keep only genuine followers, and
                            // dedup by id so a list mirrored across relays is stored once.
                            if (event is ContactListEvent && event.isTaggedUser(observer) && seen.add(event.id)) {
                                followers.add(event.pubKey)
                                answered.add(relay)
                                toPersist.trySend(event)
                            }
                        },
                    )

                client.fetchAllPagesFromPool(
                    filters = perRelay,
                    timeoutMs = config.timeoutMs,
                    maxConcurrentRelays = config.maxConcurrentRelays,
                    onRelayComplete = { relay, total ->
                        if (total > 0) log("[followers] ${relay.url}: $total kind:3 pages drained")
                    },
                ) { event, relay ->
                    // onEvent runs on the relay reader thread and must not suspend;
                    // submit() is a non-suspending channel send that backpressures.
                    if (event.kind == ContactListEvent.KIND) verifier.submit(event, relay)
                }

                // No more events will arrive: drain the verifier, then the persister.
                verifier.close()
                verifier.join()
                toPersist.close()
                persister.join()
            }

            Stats(
                relaysQueried = config.relays.size,
                relaysAnswered = answered.size,
                followersFound = followers.size,
                eventsStored = eventsStored,
                downloadMs = mark.elapsedNow().inWholeMilliseconds,
            )
        }

    companion object {
        /** Reverse-follow lookup is kind:3 only — a follower IS the author of a kind:3 that `#p`-tags the observer. */
        private val FOLLOW_KINDS = listOf(ContactListEvent.KIND)
    }
}
