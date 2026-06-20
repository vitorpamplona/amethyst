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
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.ui.feeds.home.HomeFeedKinds
import com.vitorpamplona.amethyst.commons.ui.feeds.home.HomeFeedParams
import com.vitorpamplona.amethyst.commons.ui.feeds.home.sortedByHomeFeedOrder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy notes home [--limit N] [--since TS] [--until TS] [--timeout SECS]`
 * `              [--watch [--duration SECS]]`
 *
 * Amethyst's home feed: top-level posts (and reposts) from the accounts you
 * follow, across the full `HomeNewThreadFeedFilter` kind set, with muted
 * authors / threads removed. The inclusion rules live in
 * `commons/.../ui/feeds/home/HomeFeed.kt` and are shared with the Android app.
 *
 * ## Two ways to draw a feed in a non-interactive CLI
 *
 * The Android feed is a reactive mutable list that re-sorts on every relay
 * event. A terminal stream is append-only — you can't re-sort lines already
 * printed — so this command splits into two phases:
 *
 *  - **snapshot (default):** drain to EOSE, filter + sort newest-first, cap at
 *    `--limit`, emit ONE JSON object `{notes:[…]}`. The page you'd see on open.
 *  - **`--watch`:** print the backfill page oldest-first, then keep the
 *    subscription open and emit each new live event as its own line (JSONL),
 *    `tail -f`-style, until `--duration` elapses (or SIGINT).
 */
object HomeFeedCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val limit = args.intFlag("limit", 50)
        if (limit <= 0) return Output.error("bad_args", "home: --limit must be > 0")
        val since = args.flag("since")?.toLongOrNull()
        val until = args.flag("until")?.toLongOrNull()
        val timeoutSecs = args.longFlag("timeout", 8L)
        val watch = args.bool("watch")
        val durationSecs = args.longFlag("duration", 60L)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()

            val authors = FeedCommand.resolveFollowing(ctx, timeoutSecs * 1000)
            val relays = ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() }

            // No follows (or no relays) → nothing to draw. Keep the contract:
            // an empty page in snapshot mode, an immediate finish in watch mode.
            if (authors.isEmpty() || relays.isEmpty()) {
                if (!watch) {
                    Output.emit(
                        mapOf(
                            "mode" to "home",
                            "authors" to authors,
                            "queried_relays" to relays.map { it.url },
                            "count" to 0,
                            "notes" to emptyList<Any>(),
                        ),
                    )
                } else {
                    System.err.println("[cli] home --watch: nothing to follow yet (no contacts or relays)")
                }
                return 0
            }

            val params = HomeFeedParams(authors.toSet(), loadHidden(ctx, authors, timeoutSecs * 1000))

            // ---- Phase 1: backfill ------------------------------------------
            val backfillFilter =
                Filter(
                    kinds = HomeFeedKinds,
                    authors = authors,
                    since = since,
                    until = until,
                    // Headroom: each relay caps independently, then we merge,
                    // dedup and re-cap across the union.
                    limit = (limit * 2).coerceAtMost(500),
                )
            val received = ctx.drain(relays.associateWith { listOf(backfillFilter) }, timeoutSecs * 1000)
            val page =
                received
                    .asSequence()
                    .map { it.second }
                    .filter { params.match(it) }
                    .toList()
                    .sortedByHomeFeedOrder()
                    .take(limit)

            if (!watch) {
                Output.emit(
                    mapOf(
                        "mode" to "home",
                        "authors" to authors,
                        "queried_relays" to relays.map { it.url },
                        "count" to page.size,
                        "notes" to page.map { it.toNoteMap() },
                    ),
                )
                return 0
            }

            // ---- Phase 2: live tail -----------------------------------------
            // Backfill oldest-first so the newest sits at the bottom and live
            // events append naturally below it (chat-log order).
            val seen = HashSet<HexKey>()
            for (ev in page.asReversed()) {
                seen.add(ev.id)
                Output.emitLine(ev.toNoteMap("backfill"))
            }

            // Only events stamped from "now" forward are genuinely live; relays
            // replay history until EOSE, so we de-dup against the backfill and
            // re-apply the same acceptance to anything new.
            val liveFilter =
                Filter(
                    kinds = HomeFeedKinds,
                    authors = authors,
                    since = TimeUtils.now(),
                )
            System.err.println("[cli] home --watch: streaming for ${durationSecs}s (Ctrl-C to stop)")
            ctx.stream(
                filters = relays.associateWith { listOf(liveFilter) },
                timeoutMs = durationSecs * 1000,
                onEvent = { _, event ->
                    if (seen.add(event.id) && params.match(event)) {
                        Output.emitLine(event.toNoteMap("live"))
                    }
                },
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * Build the home feed's mute state from the account's NIP-51 kind:10000
     * list — public mutes plus private mutes (Amy holds the signer). Cache
     * first; falls back to a one-shot relay drain if the list isn't local yet.
     * Best-effort: an absent list yields empty mutes (nothing hidden).
     */
    private suspend fun loadHidden(
        ctx: Context,
        authors: List<HexKey>,
        timeoutMs: Long,
    ): LiveHiddenUsers {
        val self = ctx.identity.pubKeyHex
        var mute = ctx.muteListOf(self)
        if (mute == null) {
            val relays = ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() }
            if (relays.isNotEmpty()) {
                val filter = Filter(kinds = listOf(MuteListEvent.KIND), authors = listOf(self), limit = 1)
                mute =
                    ctx
                        .drain(relays.associateWith { listOf(filter) }, timeoutMs)
                        .mapNotNull { it.second as? MuteListEvent }
                        .maxByOrNull { it.createdAt }
            }
        }
        if (mute == null) return EMPTY_HIDDEN

        val tags =
            mute.publicMutes() + (runCatching { mute.privateMutes(ctx.signer) }.getOrNull() ?: emptyList())
        val mutedUsers = tags.filterIsInstance<UserTag>().mapTo(HashSet()) { it.pubKey }
        val mutedThreads = tags.filterIsInstance<EventTag>().mapTo(HashSet()) { it.eventId }
        return EMPTY_HIDDEN.copy(hiddenUsers = mutedUsers, mutedThreads = mutedThreads)
    }

    private val EMPTY_HIDDEN =
        LiveHiddenUsers(
            showSensitiveContent = null,
            hiddenWordsCase = emptyList(),
            hiddenUsersHashCodes = emptySet(),
            spammersHashCodes = emptySet(),
        )

    /** JSON/stream shape for one note. [phase] is set only for `--watch` lines. */
    private fun Event.toNoteMap(phase: String? = null): Map<String, Any?> =
        buildMap {
            if (phase != null) put("phase", phase)
            put("id", id)
            put("pubkey", pubKey)
            put("created_at", createdAt)
            put("kind", kind)
            put("content", content)
            put("tags", tags.map { it.toList() })
        }
}
