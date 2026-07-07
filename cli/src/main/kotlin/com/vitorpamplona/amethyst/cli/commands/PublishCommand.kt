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

/**
 * `amy publish [EVENT-JSON] [--relay URL[,URL…]]` — broadcast a pre-made,
 * already-signed event (nak's `publish`). The event JSON comes from the
 * positional argument or from stdin when omitted or `-`.
 *
 * The event is verified before broadcast — a bad id/signature is rejected
 * rather than published. Targets default to the account's outbox when
 * `--relay` is not given.
 */
object PublishCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        if (json.isEmpty()) return Output.error("bad_args", "no event JSON on the argument or stdin")

        val event =
            try {
                Event.fromJson(json)
            } catch (e: Exception) {
                return Output.error("bad_args", "could not parse event JSON: ${e.message}")
            }
        if (!event.verify()) {
            return Output.error("invalid_event", "event id/signature does not verify — refusing to publish")
        }

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val targets = RawEventSupport.publishTargets(ctx, args)
            if (targets.isEmpty()) {
                return Output.error("no_relays", "no outbox relays configured; pass --relay or run `amy relay add`")
            }
            val ack = ctx.publish(event, targets)
            Output.emit(
                mapOf(
                    "event_id" to event.id,
                    "kind" to event.kind,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }
}
