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

/**
 * `amy fetch [--kind …] [--author …] [--id …] [--tag …] [--since/--until TS]
 *            [--limit N] [--search TEXT] [--relay URL[,URL…]] [--timeout SECS]`
 *
 * One-shot query: open a subscription, collect everything until every relay
 * sends EOSE (or the timeout fires), then print and exit. This is the bounded
 * half of nak's `req`; the live-streaming half is `amy subscribe`.
 *
 * Results are deduplicated by id, sorted newest-first, capped at `--limit`
 * (default 100), and emitted as full event JSON under an `events` array.
 * Relays default to the account's outbox, then the bootstrap union.
 */
object FetchCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val limit = args.flag("limit")?.toIntOrNull() ?: 100
        if (limit <= 0) return Output.error("bad_args", "--limit must be > 0")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 8L) * 1000
        val filter = RawEventSupport.buildFilter(args)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val relays = RawEventSupport.queryTargets(ctx, args)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays available; pass --relay or run `amy relay add`")

            val received = ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
            val events =
                received
                    .asSequence()
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .map { Output.mapper.readTree(it.toJson()) }
                    .toList()

            Output.emit(
                mapOf(
                    "queried_relays" to relays.map { it.url },
                    "count" to events.size,
                    "events" to events,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
