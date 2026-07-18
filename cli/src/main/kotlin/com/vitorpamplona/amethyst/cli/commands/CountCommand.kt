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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count

/**
 * `amy count [<same filter flags as fetch>] [--relay URL[,URL…]] [--timeout SECS]`
 *
 * NIP-45 COUNT: ask each relay how many events match the filter, without
 * downloading them (nak's `count`). Reports each relay's reply plus a
 * per-relay max as `total` (counts can't be deduplicated across relays, so
 * summing would over-count overlapping stores — the max is the safer single
 * number).
 *
 * Thin assembly only: the COUNT round-trip lives in quartz
 * (`INostrClient.count`); this file builds the filter and shapes output.
 */
object CountCommand {
    val USAGE: String =
        """
        |NIP-45 COUNT (per-relay match counts, no event download):
        |  count  [--kind K[,K]] [--author U[,U]]       ask each relay how many events match the
        |         [--id ID[,ID]] [--tag e=ID,p=PK,…]     filter. Same filter flags as fetch;
        |         [--since TS] [--until TS] [--limit N]  --author/--id accept npub/nevent/note/hex.
        |         [--search TEXT] [--relay URL[,URL…]]   `total` is the per-relay max (counts can't
        |         [--timeout SECS]                       be deduplicated across relays).
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        val args = Args(rest)
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 15L) * 1000
        val filter = RawEventSupport.buildFilter(args)
        args.rejectUnknown("relay")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val relays = RawEventSupport.queryTargets(ctx, args)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays available; pass --relay or run `amy relay add`")

            val results = ctx.client.count(relays.associateWith { listOf(filter) }, timeoutMs)

            Output.emit(
                mapOf(
                    "queried_relays" to relays.map { it.url },
                    "responded" to results.size,
                    "total" to (results.values.maxOfOrNull { it.count } ?: 0),
                    "per_relay" to
                        results.map { (relay, res) ->
                            mapOf(
                                "relay" to relay.url,
                                "count" to res.count,
                                "approximate" to res.approximate,
                            )
                        },
                ),
            )
            return 0
        }
    }
}
