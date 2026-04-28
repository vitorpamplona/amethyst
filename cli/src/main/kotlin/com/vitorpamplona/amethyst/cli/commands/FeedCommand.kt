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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

/**
 * `amy feed [--author USER] [--following] [--limit N] [--since TS] [--until TS]`
 *
 * Read-side counterpart to `amy post`. Three modes selected by flags:
 *
 *  - default: kind:1 by the current account.
 *  - `--author USER`: kind:1 by the given user (npub/nprofile/64-hex/NIP-05).
 *  - `--following`: fetch the current account's NIP-02 contact list and pull
 *    kind:1 from every author in it.
 *
 * Events are deduplicated by id, sorted newest-first, and capped at `--limit`
 * (default 50). The output is a JSON object with a `notes` array of
 * `{id, pubkey, created_at, content, tags}`.
 */
object FeedCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.flag("author")
        val following = args.bool("following")
        if (author != null && following) {
            return Output.error("bad_args", "feed: pass either --author or --following, not both")
        }
        val limit = args.intFlag("limit", 50)
        if (limit <= 0) return Output.error("bad_args", "feed: --limit must be > 0")
        val since = args.flag("since")?.toLongOrNull()
        val until = args.flag("until")?.toLongOrNull()
        val timeoutSecs = args.longFlag("timeout", 8L)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()

            val (authors, mode) =
                when {
                    following -> resolveFollowing(ctx, timeoutSecs * 1000) to "following"
                    author != null -> listOf(ctx.requireUserHex(author)) to "author"
                    else -> listOf(ctx.identity.pubKeyHex) to "self"
                }

            if (authors.isEmpty()) {
                Output.emit(
                    mapOf(
                        "mode" to mode,
                        "authors" to emptyList<String>(),
                        "notes" to emptyList<Any>(),
                    ),
                )
                return 0
            }

            val relays = relaysForReadingFeed(ctx, mode)
            if (relays.isEmpty()) {
                return Output.error("no_relays", "no relays available; run `amy relay add` first")
            }

            val filter =
                Filter(
                    kinds = listOf(TextNoteEvent.KIND),
                    authors = authors,
                    since = since,
                    until = until,
                    // Ask for some headroom over the requested limit because
                    // each relay applies the cap independently and we then
                    // merge + dedup across the union.
                    limit = (limit * 2).coerceAtMost(500),
                )
            val filterMap = relays.associateWith { listOf(filter) }
            val received = ctx.drain(filterMap, timeoutSecs * 1000)

            val notes =
                received
                    .asSequence()
                    .map { it.second }
                    .filter { it.kind == TextNoteEvent.KIND }
                    .filter { it.pubKey in authors.toSet() }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .map { ev ->
                        mapOf(
                            "id" to ev.id,
                            "pubkey" to ev.pubKey,
                            "created_at" to ev.createdAt,
                            "content" to ev.content,
                            "tags" to ev.tags.map { it.toList() },
                        )
                    }.toList()

            Output.emit(
                mapOf(
                    "mode" to mode,
                    "authors" to authors,
                    "queried_relays" to relays.map { it.url },
                    "count" to notes.size,
                    "notes" to notes,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * Read-side relay strategy: own / following feeds use our outbox set
     * (where we expect our follows to also publish, in NIP-65 spirit). For
     * an arbitrary `--author` we have no idea where they publish, so we
     * widen to the bootstrap union.
     */
    private suspend fun relaysForReadingFeed(
        ctx: Context,
        mode: String,
    ): Set<NormalizedRelayUrl> =
        when (mode) {
            "author" -> ctx.bootstrapRelays()
            else -> ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() }
        }

    private suspend fun resolveFollowing(
        ctx: Context,
        timeoutMs: Long,
    ): List<HexKey> {
        // Cache-first: contact lists are kind:3 (replaceable). If we've
        // ever seen ours, the local store has it already and reading is
        // a slot lookup — no relay round-trip.
        ctx.contactsOf(ctx.identity.pubKeyHex)?.let {
            return it.verifiedFollowKeySet().toList()
        }

        val relays = ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() }
        if (relays.isEmpty()) return emptyList()
        val filter =
            Filter(
                kinds = listOf(ContactListEvent.KIND),
                authors = listOf(ctx.identity.pubKeyHex),
                limit = 1,
            )
        val filterMap = relays.associateWith { listOf(filter) }
        val received = ctx.drain(filterMap, timeoutMs)
        val latest =
            received
                .mapNotNull { (_, ev) -> ev as? ContactListEvent }
                .filter { it.pubKey == ctx.identity.pubKeyHex }
                .maxByOrNull { it.createdAt }
        return latest?.verifiedFollowKeySet()?.toList() ?: emptyList()
    }
}
