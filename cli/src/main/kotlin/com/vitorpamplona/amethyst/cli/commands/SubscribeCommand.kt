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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `amy subscribe [<same filter flags as fetch>] [--relay URL[,URL…]]
 *                [--timeout SECS]`
 *
 * The live-streaming half of nak's `req`: open a subscription and print each
 * matching event as it arrives — one object per line (NDJSON under `--json`).
 * Unlike `fetch` it does not stop at EOSE; it streams until `--timeout` SECS
 * elapse, or until the process is interrupted when no timeout is given.
 *
 * Each event is signature-verified before printing; bad ones are dropped.
 */
object SubscribeCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutMs = args.flag("timeout")?.toLongOrNull()?.let { it * 1000 }
        val filter = RawEventSupport.buildFilter(args)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = RawEventSupport.queryTargets(ctx, args)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays available; pass --relay or run `amy relay add`")

            val subId = newSubId()
            val listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event.verify()) Output.emit(Output.mapper.readTree(event.toJson()))
                    }
                }

            ctx.client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)
            try {
                if (timeoutMs != null) withTimeoutOrNull(timeoutMs) { awaitCancellation() } else awaitCancellation()
            } finally {
                ctx.client.unsubscribe(subId)
            }
            return 0
        }
    }
}
