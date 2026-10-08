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
package com.vitorpamplona.amethyst.cli.commands.trust

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.commands.RawEventSupport
import com.vitorpamplona.amethyst.cli.commands.graperank.CONTENT_AGGREGATOR_RELAYS
import com.vitorpamplona.amethyst.cli.commands.graperank.fetchLatestProviderList
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.publicRows
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.withProviderRows
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes

/**
 * `amy trust copy USER [--private] [--timeout SECS]`
 *
 * Copies USER's public kind 10040 rows into the account's, replacing rows of the same names and
 * any previous score provider, keeping every other entry. USER's provider computes its cards for
 * USER, so the network then shows as USER sees it: `amy trust copy _@brainstorm.world` uses
 * Brainstorm's default view, no sign-up needed. USER's private rows are encrypted to them and
 * cannot be copied. Then `amy trust sync` downloads the network.
 */
object TrustCopy {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val who = args.positional(0, "user")
        val isPrivate = args.bool("private")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val them = ctx.requireUserHex(who)
            val theirs =
                fetchLatestProviderList(ctx, them, ctx.indexRelays() + CONTENT_AGGREGATOR_RELAYS, timeoutMs)
                    ?: return Output.error("no_list", "no kind 10040 found for $who")
            val rows = theirs.publicRows()
            if (rows.none { it.name == ProviderTypes.rank.toValue() }) {
                return Output.error("no_rank", "$who's kind 10040 names no public 30382:rank provider")
            }

            val me = ctx.identity.pubKeyHex
            val outbox = ctx.outboxRelays()
            val mine = fetchLatestProviderList(ctx, me, outbox, timeoutMs)
            val event = withProviderRows(mine, rows, isPrivate, ctx.signer)
            val ack = ctx.publish(event, outbox)
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }

            Output.emit(
                mapOf(
                    "from" to them,
                    "rows" to rows.map { it.toTagArray().toList() },
                    "private" to isPrivate,
                    "event_id" to event.id,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
