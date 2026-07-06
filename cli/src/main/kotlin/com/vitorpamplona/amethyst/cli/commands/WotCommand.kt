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
import com.vitorpamplona.amethyst.commons.wot.WoTService
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * `amy wot <get|list|sync>` — Web-of-Trust score queries.
 *
 * The score for a pubkey X is the count of accounts in the active user's
 * kind-3 follow set who also follow X. `get` and `list` are read-only —
 * they hydrate the score map from whatever kind-3 events already live in
 * the local event store. `sync` pulls fresh kind-3 events from the
 * configured relay pool so the next `get` / `list` is up to date.
 */
object WotCommand {
    suspend fun dispatch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val head = rest.firstOrNull() ?: return usage()
        val tail = rest.drop(1).toTypedArray()
        return when (head) {
            "get" -> get(dataDir, tail)
            "list" -> list(dataDir, tail)
            "sync" -> sync(dataDir, tail)
            else -> usage()
        }
    }

    private fun usage(): Int = Output.error("bad_args", "wot <get|list|sync>")

    private suspend fun get(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "wot get <pubkey|npub>")
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
        val timeoutMs = args.flag("timeout")?.toLongOrNull()?.times(1000) ?: 5_000L
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex
            val myKind3 = ctx.contactsOf(self)
            val follows =
                myKind3?.verifiedFollowKeySet()?.toList()
                    ?: return Output.error("no_follows", "no kind-3 in local store; run `amy follow` first")
            if (follows.isEmpty()) {
                Output.emit(mapOf("synced" to 0, "detail" to "empty follow set"))
                return 0
            }
            // Index relays — shared with the Desktop app via
            // `java.util.prefs`. Falls back to
            // `PreferencesIndexRelays.DEFAULT_INDEX_RELAYS` when the
            // user hasn't configured anything, so this is never empty
            // in practice.
            val relays = ctx.indexRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no index relays configured")

            // Chunk authors into ≤100 per Filter for relays with per-filter caps.
            val filters =
                follows.chunked(100).map { chunk ->
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        authors = chunk,
                        limit = chunk.size,
                    )
                }
            val received = ctx.drain(relays.associateWith { filters }, timeoutMs)
            val kind3Events = received.mapNotNull { it.second as? ContactListEvent }
            // Persist to store so future `get` / `list` see them.
            kind3Events.forEach { runCatching { ctx.store.insert(it) } }
            Output.emit(mapOf("received" to kind3Events.size, "followers" to follows.size))
            return 0
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
