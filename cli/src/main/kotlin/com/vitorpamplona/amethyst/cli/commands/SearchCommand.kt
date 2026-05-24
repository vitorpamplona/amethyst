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
import com.vitorpamplona.amethyst.commons.actions.SearchActions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent

/**
 * `amy search <user|note> <query>` — NIP-50 full-text search across the
 * caller's configured search relays (kind:10007) or, when none is set,
 * Amethyst's curated default search-relay list.
 *
 * Two subcommands:
 *   * `search user <query>` drains kind:0 metadata events whose content
 *     matches [query] — useful for resolving a partial display name to
 *     an npub before a follow / DM.
 *   * `search note <query>` drains kind:1 short text notes matching
 *     [query]. Use `--kinds 1,30023` to widen to long-form articles.
 *
 * Output is the raw relay-side hit set deduped by event id and sorted
 * by `created_at` descending. Client-side pseudo-kind filters
 * (`reply` / `media`) live in
 * [com.vitorpamplona.amethyst.commons.search.SearchResultFilter] and
 * are not exposed here yet.
 */
object SearchCommand {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "search <user|note> <query> [--limit N] [--timeout SECS]")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "user" -> searchUsers(dataDir, rest)
            "note" -> searchNotes(dataDir, rest)
            else -> Output.error("bad_args", "search ${tail[0]} — expected user|note")
        }
    }

    private suspend fun searchUsers(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "search user <query> [--limit N] [--timeout SECS]")
        val query = rest[0]
        val args = Args(rest.drop(1).toTypedArray())
        val limit = args.longFlag("limit", 20L).toInt()
        val timeoutMs = args.longFlag("timeout", 8L) * 1000

        val filter =
            SearchActions.searchProfilesFilter(query, limit)
                ?: return Output.error("bad_args", "query must not be blank")

        return runSearch(dataDir, query, filter, timeoutMs) { events ->
            events
                .mapNotNull { it as? MetadataEvent }
                .map { ev ->
                    val parsed =
                        try {
                            Output.mapper.readTree(ev.content)
                        } catch (_: Exception) {
                            null
                        }
                    mapOf(
                        "event_id" to ev.id,
                        "pubkey" to ev.pubKey,
                        "created_at" to ev.createdAt,
                        "metadata" to (parsed ?: emptyMap<String, Any?>()),
                    )
                }
        }
    }

    private suspend fun searchNotes(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "search note <query> [--limit N] [--timeout SECS] [--kinds K[,K…]]")
        val query = rest[0]
        val args = Args(rest.drop(1).toTypedArray())
        val limit = args.longFlag("limit", 50L).toInt()
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        val kindList =
            args.flags["kinds"]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: SearchActions.DEFAULT_NOTE_KINDS

        val filter =
            SearchActions.searchNotesFilter(query, kinds = kindList, limit = limit)
                ?: return Output.error("bad_args", "query must not be blank")

        return runSearch(dataDir, query, filter, timeoutMs) { events ->
            events
                .filter { it.kind in kindList }
                .map { ev ->
                    mapOf(
                        "event_id" to ev.id,
                        "pubkey" to ev.pubKey,
                        "kind" to ev.kind,
                        "created_at" to ev.createdAt,
                        "content" to ev.content,
                    )
                }
        }
    }

    private suspend fun runSearch(
        dataDir: DataDir,
        query: String,
        filter: Filter,
        timeoutMs: Long,
        render: (List<Event>) -> List<Map<String, Any?>>,
    ): Int {
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val relays =
                SearchActions.resolveSearchRelays(
                    signer = ctx.signer,
                    currentList = loadOwnSearchList(ctx),
                )
            if (relays.isEmpty()) {
                return Output.error("no_relays", "no search relays available (no kind:10007 and DefaultSearchRelayList is empty?)")
            }

            val received = ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
            val deduped =
                received
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }

            Output.emit(
                mapOf(
                    "query" to query,
                    "queried_relays" to relays.map { it.url },
                    "match_count" to deduped.size,
                    "results" to render(deduped),
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * Pull the caller's own kind:10007 from the local store. Returns null
     * when amy has never observed one — caller falls back to
     * [com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList]
     * via [SearchActions.resolveSearchRelays].
     */
    private suspend fun loadOwnSearchList(ctx: Context): SearchRelayListEvent? =
        ctx.store
            .query<Event>(
                Filter(
                    authors = listOf(ctx.identity.pubKeyHex),
                    kinds = listOf(SearchRelayListEvent.KIND),
                    limit = 1,
                ),
            ).firstOrNull() as? SearchRelayListEvent
}
