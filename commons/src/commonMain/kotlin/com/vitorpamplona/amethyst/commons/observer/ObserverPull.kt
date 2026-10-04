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
package com.vitorpamplona.amethyst.commons.observer

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads one reader's day off the search relay, through their own lens.
 *
 * A port of the Observer generator's `Pull` + `ReadinessProbe`, on Amethyst's
 * own [INostrClient] and quartz's `fetchAll`/`count` accessories, so the phone
 * talks to the relay directly — no Observer server and no model in between.
 *
 * Two of the relay's rules are load-bearing and were measured by the Observer,
 * not guessed:
 *  - Every query must name an `observer:<pk>` or say `include:spam`: the relay
 *    CLOSES tokenless queries ("auth-required: … no house observer to lend
 *    you"), which reads as an empty answer.
 *  - A NIP-50 search with no `since` times out on this store. Every ranked
 *    read here carries the 24-hour window.
 */
class ObserverPull(
    private val client: INostrClient,
    private val searchRelay: NormalizedRelayUrl = DEFAULT_SEARCH_RELAY,
    private val trustFloor: Int = DEFAULT_TRUST_FLOOR,
) {
    /** Every desk's progress, reported as each one lands. */
    fun interface Progress {
        fun onDesk(
            done: Int,
            total: Int,
        )
    }

    suspend fun readiness(
        reader: HexKey,
        since: Long,
    ): ObserverReadiness.Facts =
        coroutineScope {
            val scoreList =
                async {
                    fetch(Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(reader), limit = 1, search = INCLUDE_SPAM))
                        .maxByOrNull { it.createdAt }
                }
            val lensed = async { fetch(probe(reader, since)).size }
            val anonymous = async { fetch(probe(null, since)).size }

            val list = scoreList.await()
            ObserverReadiness.Facts(
                scoreListSeen = list != null,
                // quartz's tag parser requires all three fields, so a hintless entry
                // is rejected there — the same parser the relay resolves it with.
                rankService =
                    list
                        ?.tags
                        ?.serviceProviders()
                        ?.firstOrNull { it.service == ProviderTypes.rank }
                        ?.pubkey,
                probeLensed = lensed.await(),
                probeAnonymous = anonymous.await(),
            )
        }

    suspend fun corpus(
        reader: HexKey,
        since: Long,
        until: Long,
        progress: Progress = Progress { _, _ -> },
    ): ObserverCorpus =
        coroutineScope {
            val desks = ObserverDesk.entries
            val done = MutableStateFlow(0)
            progress.onDesk(0, desks.size)

            // ONE REQ PER DESK. Merged, two desks sharing a kind collide; asked
            // separately, each answer arrives already attributed.
            val asked =
                desks.map { desk ->
                    async {
                        val events =
                            fetch(ranked(desk.kinds, since, until, desk.limit, reader), DESK_IDLE_MS)
                                // The reader's own posts are not the news: they rank highly through
                                // their own lens almost by construction and would crowd the page.
                                .filter { desk.keeps(it) && it.pubKey != reader }
                                .take(desk.limit)
                        progress.onDesk(done.updateAndGet { it + 1 }, desks.size)
                        desk to events
                    }
                }
            // One COUNT, for an honest denominator — with NO limit, or the relay counts
            // to the cap and stops. Null is a supported answer and must not become a guess.
            val dayNotes =
                async {
                    withTimeoutOrNull(COUNT_DEADLINE_MS) {
                        runCatching { client.count(searchRelay, ranked(ObserverDesk.NOTES.kinds, since, until, null, reader))?.count?.toLong() }.getOrNull()
                    }
                }

            val ranked = asked.awaitAll().toMap()
            ObserverCorpus(
                reader = reader,
                since = since,
                until = until,
                ranked = ranked,
                engagement = emptyMap(),
                names = emptyMap(),
                dayNotes = dayNotes.await(),
            )
        }

    /**
     * What people did with each story today: reactions, reposts and replies
     * through the SAME lens and trust floor, so a reply from a stranger the
     * lens does not vouch for counts for nothing; and zaps, unranked.
     *
     * Measured on search.brainstorm.world, 2026-10-04, one 24-hour window: it
     * mirrors replies, but held 2 kind-7 reactions and no kind-6/16 reposts at
     * all — so on this relay the lensed signal is in practice replies. Zap
     * receipts ARE mirrored (1,325 in the window). They are asked with
     * `include:spam` rather than through the lens because a receipt is signed by
     * the recipient's Lightning server, not by the person who zapped, so a trust
     * floor on its author measures the wrong key; a zap costs its sender real
     * sats, which is its own spam filter. Reactions and reposts are still asked
     * for, so the page ranks better the day the relay starts mirroring them.
     *
     * This is the native editor's replacement for the model's judgement. The
     * relay selects by trust score but delivers by `created_at`, and the score
     * is not in the response — so without this the page could only be sorted
     * by time.
     */
    suspend fun engagement(
        reader: HexKey,
        since: Long,
        until: Long,
        events: List<Event>,
    ): Map<String, ObserverEngagement> {
        val ids = events.filter { it !is AddressableEvent }.map { it.id }.distinct()
        val addresses = events.filterIsInstance<AddressableEvent>().map { it.addressTag() }.distinct()
        if (ids.isEmpty() && addresses.isEmpty()) return emptyMap()

        val lens = "observer:$reader sort:rank filter:rank:gte:$trustFloor"

        fun filters(
            kinds: List<Int>,
            search: String,
        ) = ids.chunked(ENGAGEMENT_CHUNK).map { chunk ->
            Filter(kinds = kinds, tags = mapOf("e" to chunk), since = since, until = until, limit = ENGAGEMENT_LIMIT, search = search)
        } +
            addresses.chunked(ENGAGEMENT_CHUNK).map { chunk ->
                Filter(kinds = kinds, tags = mapOf("a" to chunk), since = since, until = until, limit = ENGAGEMENT_LIMIT, search = search)
            }

        val filters = filters(REACTION_KINDS, lens) + filters(ZAP_KINDS, INCLUDE_SPAM)

        val found =
            coroutineScope {
                filters
                    .map { async { fetch(it, DESK_IDLE_MS) } }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.id }
            }
        return tally(found, ids.toSet(), addresses.toSet())
    }

    /** kind 0 for everyone the page will name, batched; newest wins. */
    suspend fun names(pubkeys: Collection<HexKey>): Map<HexKey, String> {
        if (pubkeys.isEmpty()) return emptyMap()
        val found =
            coroutineScope {
                pubkeys
                    .distinct()
                    .chunked(PROFILE_CHUNK)
                    .map { chunk -> async { fetch(Filter(kinds = listOf(MetadataEvent.KIND), authors = chunk, search = INCLUDE_SPAM), PROFILE_IDLE_MS) } }
                    .awaitAll()
                    .flatten()
            }
        val best = mutableMapOf<HexKey, Event>()
        found.forEach { event ->
            val seen = best[event.pubKey]
            if (seen == null || seen.createdAt < event.createdAt) best[event.pubKey] = event
        }
        return best
            .mapNotNull { (pubkey, event) ->
                val meta = event as? MetadataEvent ?: MetadataEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig)
                val name =
                    runCatching { meta.contactMetaData()?.bestName() }
                        .getOrNull()
                        ?.replace(WHITESPACE, " ")
                        ?.trim()
                        ?.take(MAX_NAME)
                        ?.takeIf { it.isNotBlank() }
                name?.let { pubkey to it }
            }.toMap()
    }

    /**
     * A bare `observer:<pk> sort:rank` with no search term is a valid NIP-50
     * query and returns a ranked recency feed. That is the whole product.
     * `filter:rank:gte` is the trust floor, and the Observer measured that it
     * is NOT redundant with `limit`: at limit 400 it replaced 49 of the 400.
     */
    private fun ranked(
        kinds: List<Int>,
        since: Long,
        until: Long,
        limit: Int?,
        reader: HexKey,
    ) = Filter(
        kinds = kinds,
        since = since,
        until = until,
        limit = limit,
        search = "observer:$reader sort:rank filter:rank:gte:$trustFloor",
    )

    /**
     * The same question asked through the lens and without it. An unresolvable
     * token silently becomes the anonymous ranking, so the two only differ —
     * lensed empty, anonymous not — when the cards exist but are not projected.
     */
    private fun probe(
        reader: HexKey?,
        since: Long,
    ) = Filter(
        kinds = listOf(1),
        since = since,
        limit = 12,
        search = if (reader == null) "$INCLUDE_SPAM sort:rank" else "observer:$reader sort:rank",
    )

    /** A wall clock over quartz's idle clock: no read may block the press forever. */
    private suspend fun fetch(
        filter: Filter,
        idleMs: Long = PROBE_IDLE_MS,
    ): List<Event> =
        withTimeoutOrNull(idleMs * 2 + 5_000) {
            runCatching { client.fetchAll(searchRelay, filter, idleMs) }.getOrDefault(emptyList())
        } ?: emptyList()

    companion object {
        val DEFAULT_SEARCH_RELAY: NormalizedRelayUrl = RelayUrlNormalizer.normalize("wss://search.brainstorm.world")

        /** Opens the search relay to a query that names no observer. Only ever sent there. */
        const val INCLUDE_SPAM = "include:spam"

        /** The Observer's measured default: 35,084 → 11,838 notes in one window, the rest unvouched-for. */
        const val DEFAULT_TRUST_FLOOR = 20

        val REACTION_KINDS = listOf(1, 6, 7, 16)
        val ZAP_KINDS = listOf(9735)

        /** ~67 bytes per id; 300 keeps one filter near 20 KB, far under the relay's 262 KB frame. */
        private const val ENGAGEMENT_CHUNK = 300
        private const val ENGAGEMENT_LIMIT = 5000
        private const val PROFILE_CHUNK = 100
        private const val MAX_NAME = 60

        private const val PROBE_IDLE_MS = 15_000L
        private const val DESK_IDLE_MS = 25_000L
        private const val PROFILE_IDLE_MS = 20_000L
        private const val COUNT_DEADLINE_MS = 20_000L

        private val WHITESPACE = Regex("""\s+""")

        /**
         * Counts each person once per story per kind of signal: five likes from
         * one account are one like. A reply is a kind 1 whose `e`/`a` points at
         * the story; a quote (`q`) is not counted, it is its own post.
         */
        internal fun tally(
            found: List<Event>,
            ids: Set<String>,
            addresses: Set<String>,
        ): Map<String, ObserverEngagement> {
            val reactions = mutableMapOf<String, MutableSet<HexKey>>()
            val reposts = mutableMapOf<String, MutableSet<HexKey>>()
            val replies = mutableMapOf<String, MutableSet<HexKey>>()
            val zaps = mutableMapOf<String, MutableSet<HexKey>>()

            found.forEach { event ->
                val bucket =
                    when (event.kind) {
                        7 -> reactions
                        6, 16 -> reposts
                        1 -> replies
                        9735 -> zaps
                        else -> return@forEach
                    }
                val who = if (event.kind == 9735) zapSender(event) else event.pubKey
                val targets =
                    event.tags
                        .mapNotNull { tag ->
                            if (tag.size < 2) return@mapNotNull null
                            when (tag[0]) {
                                "e" -> tag[1].takeIf { it in ids && (event.kind != 1 || tag.getOrNull(3) != "mention") }
                                "a" -> tag[1].takeIf { it in addresses }
                                else -> null
                            }
                        }.distinct()
                targets.forEach { bucket.getOrPut(it) { mutableSetOf() }.add(who) }
            }

            return (reactions.keys + reposts.keys + replies.keys + zaps.keys).associateWith {
                ObserverEngagement(
                    reactions = reactions[it]?.size ?: 0,
                    reposts = reposts[it]?.size ?: 0,
                    replies = replies[it]?.size ?: 0,
                    zaps = zaps[it]?.size ?: 0,
                )
            }
        }

        /**
         * Who paid, not who signed: a NIP-57 receipt is signed by the Lightning
         * server. The sender is the `P` tag when the server wrote one, else the
         * pubkey inside the embedded zap request; a receipt that names neither
         * counts once under the server's key rather than not at all.
         */
        internal fun zapSender(receipt: Event): HexKey =
            receipt.tagValue("P")?.takeIf { it.length == 64 }
                ?: receipt.tagValue("description")?.let { ZAP_REQUEST_PUBKEY.find(it)?.groupValues?.get(1) }
                ?: receipt.pubKey

        private val ZAP_REQUEST_PUBKEY = Regex(""""pubkey"\s*:\s*"([0-9a-f]{64})"""")
    }
}
