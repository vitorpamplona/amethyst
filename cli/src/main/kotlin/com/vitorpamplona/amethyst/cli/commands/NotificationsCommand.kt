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
import com.vitorpamplona.amethyst.commons.ui.feeds.notifications.NotificationFeedKinds
import com.vitorpamplona.amethyst.commons.ui.feeds.notifications.NotificationFeedParams
import com.vitorpamplona.amethyst.commons.ui.feeds.notifications.sortedByNotificationFeedOrder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy notifications [--limit N] [--since TS] [--until TS] [--timeout SECS]`
 * `                  [--watch [--duration SECS]]`
 *
 * Amethyst's Notifications feed: events that p-tag you — reactions, reposts,
 * zaps, replies, mentions, comments — across the full notification kind set,
 * minus your own (zaps excepted) and muted authors. Reproduces Amethyst's
 * **Global** notification mode; the inclusion rules live in
 * `commons/.../ui/feeds/notifications/NotificationFeed.kt` and are shared with
 * the Android app.
 *
 * Same two-phase drawing model as `notes home` (a terminal stream is
 * append-only, so the live view can't re-sort): a **snapshot** (one JSON
 * object) by default, or a **live JSONL tail** under `--watch`.
 *
 * The query is a single `{"#p":[me], "kinds":[…]}` REQ against your inbox
 * relays — the p-tag gate captures reactions/reposts/zaps (which tag the note
 * author) and replies/mentions alike.
 */
object NotificationsCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val limit = args.intFlag("limit", 50)
        if (limit <= 0) return Output.error("bad_args", "notifications: --limit must be > 0")
        val since = args.flag("since")?.toLongOrNull()
        val until = args.flag("until")?.toLongOrNull()
        val timeoutSecs = args.longFlag("timeout", 8L)
        val watch = args.bool("watch")
        val durationSecs = args.longFlag("duration", 60L)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()

            val me = ctx.identity.pubKeyHex
            // Notifications arrive on the relays you read from (NIP-65 inbox).
            val relays = ctx.inboxRelays().ifEmpty { ctx.bootstrapRelays() }
            if (relays.isEmpty()) {
                if (!watch) {
                    Output.emit(
                        mapOf(
                            "mode" to "notifications",
                            "pubkey" to me,
                            "queried_relays" to emptyList<String>(),
                            "count" to 0,
                            "notes" to emptyList<Any>(),
                        ),
                    )
                } else {
                    System.err.println("[cli] notifications --watch: no relays to query (configure relays first)")
                }
                return 0
            }

            val params = NotificationFeedParams(me, ctx.hiddenUsers(timeoutSecs * 1000))

            // ---- Phase 1: backfill ------------------------------------------
            val backfillFilter =
                Filter(
                    kinds = NotificationFeedKinds.toList(),
                    tags = mapOf("p" to listOf(me)),
                    since = since,
                    until = until,
                    limit = (limit * 2).coerceAtMost(500),
                )
            val received = ctx.drain(relays.associateWith { listOf(backfillFilter) }, timeoutSecs * 1000)
            val page =
                received
                    .asSequence()
                    .map { it.second }
                    .filter { params.match(it) }
                    .toList()
                    .sortedByNotificationFeedOrder()
                    .take(limit)

            if (!watch) {
                Output.emit(
                    mapOf(
                        "mode" to "notifications",
                        "pubkey" to me,
                        "queried_relays" to relays.map { it.url },
                        "count" to page.size,
                        "notes" to page.map { it.toNotificationMap() },
                    ),
                )
                return 0
            }

            // ---- Phase 2: live tail -----------------------------------------
            // Backfill oldest-first so live events append below it (chat-log order).
            val seen = HashSet<HexKey>()
            for (ev in page.asReversed()) {
                seen.add(ev.id)
                Output.emitLine(ev.toNotificationMap("backfill"))
            }

            val liveFilter =
                Filter(
                    kinds = NotificationFeedKinds.toList(),
                    tags = mapOf("p" to listOf(me)),
                    since = TimeUtils.now(),
                )
            System.err.println("[cli] notifications --watch: streaming for ${durationSecs}s (Ctrl-C to stop)")
            ctx.stream(
                filters = relays.associateWith { listOf(liveFilter) },
                timeoutMs = durationSecs * 1000,
                onEvent = { _, event ->
                    if (seen.add(event.id) && params.match(event)) {
                        Output.emitLine(event.toNotificationMap("live"))
                    }
                },
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /** JSON/stream shape for one notification. [phase] is set only for `--watch` lines. */
    private fun Event.toNotificationMap(phase: String? = null): Map<String, Any?> =
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
