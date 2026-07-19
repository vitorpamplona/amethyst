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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.wot.OutboxCacheGateway
import com.vitorpamplona.amethyst.commons.wot.OutboxDispatcher
import com.vitorpamplona.amethyst.commons.wot.WoTService
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.Collections

/**
 * `amy fof <get|list|sync>` — follows-of-follows social-proof scores.
 *
 * The score for a pubkey X is the count of accounts in the active user's
 * kind:3 follow set who also follow X — cheap single-hop social proof, NOT
 * the computed web of trust (that's `amy graperank`). `get` and `list` are
 * read-only — they hydrate the score map from whatever kind:3 events already
 * live in the local event store. `sync` pulls fresh kind:3 events from the
 * configured relay pool so the next `get` / `list` is up to date.
 *
 *  - `fof get USER`  — X's score (how many of your follows follow X).
 *  - `fof list`      — accounts ranked by score (discovery: who's most
 *                      followed inside your network).
 *  - `fof sync`      — refresh your follows' kind:3 from relays.
 */
object FofCommand {
    val USAGE: String =
        """
        |amy fof — follows-of-follows (social proof — the cheap counterpart to graperank)
        |
        |  fof get USER                               USER's score: how many accounts you follow also
        |                                              follow them (single-hop social proof, not trust).
        |  fof list [--threshold N] [--limit N]       accounts ranked by that score — who's most
        |                                              followed inside your network (default N: 1 / 50).
        |  fof sync [--timeout SECS]                  refresh your follows' kind:3 from the index relays
        |                                              so the next get/list is current.
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val head = rest.firstOrNull() ?: return usage()
        val tail = rest.drop(1).toTypedArray()
        return when (head) {
            "--help", "-h", "help" -> {
                System.err.println(USAGE)
                0
            }
            "get" -> get(dataDir, tail)
            "list" -> list(dataDir, tail)
            "sync" -> sync(dataDir, tail)
            else -> usage()
        }
    }

    private fun usage(): Int = Output.error("bad_args", "fof <get|list|sync>")

    private suspend fun get(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "fof get <pubkey|npub>")
        val userArg = rest[0]
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val target = ctx.requireUserHex(userArg)
            val (svc, scope) = buildHydratedService(ctx)
            try {
                val score = svc.scoresSnapshot()[target] ?: 0
                Output.emit(mapOf("pubkey" to target, "score" to score))
                return 0
            } finally {
                scope.cancel()
            }
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val threshold = args.flag("threshold")?.toIntOrNull() ?: 1
        val limit = args.flag("limit")?.toIntOrNull() ?: 50
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (svc, scope) = buildHydratedService(ctx)
            try {
                val entries =
                    svc
                        .scoresSnapshot()
                        .entries
                        .asSequence()
                        .filter { it.value >= threshold }
                        .sortedByDescending { it.value }
                        .take(limit)
                        .map { mapOf("pubkey" to it.key, "score" to it.value) }
                        .toList()
                Output.emit(mapOf("count" to entries.size, "entries" to entries))
                return 0
            } finally {
                scope.cancel()
            }
        }
    }

    private suspend fun sync(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        // Overall timeout; per-relay budget is set by OutboxDispatcher's
        // default (4s). `--timeout N` overrides the overall cap.
        val overallTimeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex
            val myKind3 = ctx.contactsOf(self)
            val follows =
                myKind3?.verifiedFollowKeySet()?.toSet()
                    ?: return Output.error("no_follows", "no kind-3 in local store; run `amy follow` first")
            if (follows.isEmpty()) {
                Output.emit(mapOf("synced" to 0, "detail" to "empty follow set"))
                return 0
            }
            val relays = ctx.indexRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no index relays configured")

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                // Buffer discovered events; persist synchronously after
                // the fetch. `store.insert` is suspending so we can't call
                // it from the non-suspending gateway callbacks. This also
                // keeps `insert` errors surfaceable in a single log line
                // rather than swallowed into a race.
                val buffered = Collections.synchronizedList(mutableListOf<Event>())
                val gateway =
                    object : OutboxCacheGateway {
                        override fun cachedOutbox(pubkey: HexKey): AdvertisedRelayListEvent? =
                            // Amy's store lookup is suspending; can't do
                            // it here. The dispatcher then falls through
                            // to Phase 1 discovery for every author, which
                            // matches the `fof sync` behaviour of always
                            // re-asking. A future optimisation
                            // could pre-populate a `Map<HexKey,
                            // AdvertisedRelayListEvent>` before dispatch.
                            null

                        override fun onOutboxDiscovered(
                            event: AdvertisedRelayListEvent,
                            relay: NormalizedRelayUrl,
                        ) {
                            buffered.add(event)
                        }

                        override fun onDiscoveredEvent(
                            event: Event,
                            relay: NormalizedRelayUrl,
                        ) {
                            buffered.add(event)
                        }
                    }

                val dispatcher =
                    OutboxDispatcher(
                        client = ctx.client,
                        scope = scope,
                        indexRelays = { relays },
                        gateway = gateway,
                        overallTimeoutMs = overallTimeoutMs,
                    )

                val result = dispatcher.fetchKind3Only(follows)

                // Persist to store so future `get` / `list` see them.
                val eventsToPersist = synchronized(buffered) { buffered.toList() }
                eventsToPersist.forEach { runCatching { ctx.store.insert(it) } }

                Output.emit(
                    mapOf(
                        "followers" to follows.size,
                        "authors_requested" to result.authorsRequested,
                        "kind10002_received" to result.kind10002Received,
                        "kind3_received" to result.kind3Received,
                        "outbox_covered_authors" to result.outboxCoveredAuthors,
                        "fallback_authors" to result.fallbackAuthors,
                        "persisted" to eventsToPersist.size,
                    ),
                )
                return 0
            } finally {
                scope.cancel()
            }
        }
    }

    /**
     * Build a [WoTService], populate it from the local event store, then
     * return the (service, backing scope). Caller must cancel the scope
     * when done.
     */
    private suspend fun buildHydratedService(ctx: Context): Pair<WoTService, CoroutineScope> {
        val self = ctx.identity.pubKeyHex
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val svc = WoTService(scope, writerDispatcher = Dispatchers.Unconfined)
        val myKind3 = ctx.contactsOf(self)
        val follows: Set<HexKey> = myKind3?.verifiedFollowKeySet() ?: emptySet()
        svc.onFollowSetChange(follows, self)
        // Pull each follower's kind-3 from the store and feed into the service.
        follows.forEach { follower ->
            val followerKind3 = ctx.contactsOf(follower) ?: return@forEach
            svc.applyKind3(follower, followerKind3.verifiedFollowKeySet())
        }
        svc.markReadyOnce()
        return svc to scope
    }
}
