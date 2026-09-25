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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.cep06Announcements.AnnouncedTools
import com.vitorpamplona.quartz.contextvm.cep06Announcements.DiscoverySurface
import com.vitorpamplona.quartz.contextvm.cep06Announcements.ServerAnnouncement
import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorAdvertisement
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The name a coordinator announces for itself, or null if it announces none.
 *
 * The same rule [CordnCoordinatorDiscovery] applies when it builds a
 * [DiscoveredCoordinator]: the CEP-6 announcement kinds are replaceable, so the
 * newest per kind wins, and the name lives on the server announcement's
 * discovery surface. Shared rather than re-derived, because "newest wins" is
 * the part that is easy to get subtly wrong when a lagging relay answers late.
 *
 * Its own function because a coordinator this account already uses never goes
 * through discovery again, and until this existed there was nowhere for its
 * announced name to come from: the announcement is a raw [Event] with no
 * registered event class, so nothing caches one per coordinator.
 */
fun announcedServerName(events: List<Event>): String? =
    ServerAnnouncement
        .latestPerKind(events)[CvmKinds.SERVER_ANNOUNCEMENT]
        ?.discovery
        ?.name
        ?.takeIf { it.isNotBlank() }

/**
 * A coordinator found by listening for CEP-6 announcements.
 *
 * ## Everything here except [pubKey] and [relays] is a claim
 *
 * [surface] is what the coordinator said about itself in a self-signed event.
 * A name is not an identity (`spec/00.md` §8.5 — the pubkey is), a website is
 * not a proof of anything, and the capability flags are promises. Present this
 * as "it says", never as fact, and never branch on it.
 *
 * [relays] is the one field the announcement did *not* provide, and the reason
 * this type exists rather than a bare [ServerAnnouncement]. A CEP-6
 * announcement carries no routing information at all, so there is nothing in
 * it that says where to reach the coordinator. What we know instead is which
 * relays *delivered* it — the coordinator publishes there, so a request
 * published there is one it is positioned to see. That is an observation, not
 * a promise, and it is the best the protocol offers today.
 */
data class DiscoveredCoordinator(
    val pubKey: HexKey,
    /** Relays that delivered an announcement for this pubkey. Never empty. */
    val relays: List<NormalizedRelayUrl>,
    val surface: DiscoverySurface,
    /** The eleven tools it advertised, plus whatever else it serves. */
    val tools: Set<String>,
    /** The newest announcement's `created_at`, for judging staleness. */
    val announcedAt: Long,
) {
    /**
     * What to add to the account.
     *
     * [CoordinatorConfig.label] is left null on purpose. A label is the user's
     * word for a coordinator; the announced name is the coordinator's word for
     * itself, and copying one into the other would launder a claim into
     * something the UI presents as the user's own choice.
     */
    fun toConfig(): CoordinatorConfig =
        CoordinatorConfig(
            pubKey = pubKey,
            relays = relays,
            origin = CoordinatorConfig.Origin.ANNOUNCEMENT,
        )
}

/**
 * Finds coordinators by reading CEP-6 announcements off relays.
 *
 * ## Why this is a relay query and not a coordinator call
 *
 * [CoordinatorHealth] never polls, because a call to a coordinator is metadata
 * it gets to see (§8). This is the opposite shape: it reads events the
 * coordinators already published, and touches no coordinator at all. Nothing
 * discovered here learns that this account exists. The relays do see a query
 * for kinds 11316/11317, which says "this user is shopping for an MCP server"
 * and nothing about which one is chosen.
 *
 * ## Why the toolset is the filter
 *
 * There is no cordn marker on an announcement — see [CoordinatorAdvertisement].
 * A server is recognised by serving the eleven tools of `spec/00.md`. Measured
 * on the public relays, that separates coordinators from unrelated MCP servers
 * cleanly, and it is the only signal available.
 *
 * ## What this list is not
 *
 * It is not a directory, a ranking, or a set of recommendations, and an
 * announcement is not a sign of life: a coordinator that stopped running a
 * month ago still has its replaceable announcement sitting on the relay, and a
 * coordinator that never announced (the reference deployment's default is
 * off) is absent from it entirely. [announcedAt] is exposed so a caller can
 * show the age rather than imply freshness — this deliberately does not filter
 * by age, because a quiet coordinator and a dead one look identical from here
 * and only the user knows which theirs is.
 */
class CordnCoordinatorDiscovery(
    private val client: INostrClient,
) {
    /**
     * Reads announcements from [relays] and returns the coordinators among them.
     *
     * [limit] caps each relay's response — these are replaceable events, so the
     * cap bounds distinct servers rather than history. Results are newest
     * announcement first.
     *
     * ## One fetch per relay, on purpose
     *
     * `fetchAllWithHooks` deduplicates by event id across every relay in one
     * call, keeping only the first `(relay, event)` pair for an id. That is
     * right for reading content and wrong here, where the whole point is to
     * learn *every* relay that carries a coordinator's announcement — a single
     * call would credit each announcement to whichever relay answered first
     * and silently drop the rest of the reachable set. Querying each relay in
     * its own call scopes the dedup to that relay, so attribution is correct
     * by construction. The calls run concurrently, so this costs no wall time.
     */
    suspend fun discover(
        relays: Set<NormalizedRelayUrl>,
        limit: Int = DEFAULT_LIMIT,
        idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    ): Result =
        coroutineScope {
            if (relays.isEmpty()) return@coroutineScope Result(emptyList(), emptySet())

            val filters = listOf(Filter(kinds = CvmKinds.ANNOUNCEMENTS.toList(), limit = limit))

            val perRelay =
                relays
                    .map { relay ->
                        async {
                            client.fetchAllWithHooks(
                                filters = mapOf(relay to filters),
                                idleTimeoutMs = idleTimeoutMs,
                                onEvent = { _, _ -> true },
                            )
                        }
                    }.awaitAll()

            Result(
                coordinators = coordinatorsIn(perRelay.flatMap { it.events }),
                unreachable = perRelay.flatMapTo(mutableSetOf()) { it.stalled + it.dead.keys },
            )
        }

    /**
     * The coordinators among `(relay, event)` pairs.
     *
     * Split out from [discover] so the grouping rules are testable without a
     * relay: an announcement pair is only a coordinator when the 11317 half
     * carries the eleven tools, and a 11316 alone says nothing about what a
     * server serves.
     */
    fun coordinatorsIn(events: List<Pair<NormalizedRelayUrl, Event>>): List<DiscoveredCoordinator> {
        val byPubKey = mutableMapOf<HexKey, MutableList<Pair<NormalizedRelayUrl, ServerAnnouncement>>>()
        events.forEach { (relay, event) ->
            ServerAnnouncement.parseOrNull(event)?.let {
                byPubKey.getOrPut(it.pubKey) { mutableListOf() }.add(relay to it)
            }
        }

        return byPubKey
            .mapNotNull { (pubKey, heard) ->
                val latest = ServerAnnouncement.latestOf(heard.map { it.second })
                val tools =
                    latest[CvmKinds.TOOLS_LIST]
                        ?.let { AnnouncedTools.parseOrNull(it.content) }
                        ?.takeIf(CoordinatorAdvertisement::matches)
                        ?: return@mapNotNull null

                val server = latest[CvmKinds.SERVER_ANNOUNCEMENT]

                DiscoveredCoordinator(
                    pubKey = pubKey,
                    // Deduplicated and ordered by first hearing, so the relay
                    // that answered first is the one tried first.
                    relays = heard.map { it.first }.distinct(),
                    surface = server?.discovery ?: DiscoverySurface(),
                    tools = tools.names,
                    announcedAt = latest.values.maxOf { it.createdAt },
                )
            }.sortedByDescending { it.announcedAt }
    }

    /** What one [discover] call learned, including who did not answer. */
    data class Result(
        val coordinators: List<DiscoveredCoordinator>,
        /**
         * Relays that stalled or failed.
         *
         * An empty [coordinators] means "nobody is announcing" only when this
         * is empty too; otherwise it means "we were not told".
         */
        val unreachable: Set<NormalizedRelayUrl>,
    )

    companion object {
        const val DEFAULT_LIMIT = 200
        const val DEFAULT_IDLE_TIMEOUT_MS = 8_000L
    }
}
