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
 * `amy publish [EVENT-JSON] [--relay URL[,URL…]]` — broadcast pre-made,
 * already-signed events (nak's `publish`).
 *
 * The events come from the positional argument, from `--file PATH`, or from
 * stdin when the argument is omitted or `-`. One event or many: a single JSON
 * object, JSONL (one per line — the shape `amy fetch` prints), or a JSON array
 * all work, so `amy fetch --kind 1 --all | amy publish --relay wss://…`
 * mirrors a whole result set onto another relay.
 *
 * Every event is verified before broadcast — a bad id/signature is reported
 * and skipped rather than published. Targets default to the account's outbox
 * when `--relay` is not given.
 */
object PublishCommand {
    val USAGE: String =
        """
        |amy publish — broadcast pre-made signed events
        |
        |  publish [EVENT-JSON] [--relay URL[,URL…]]   broadcast one or more pre-made signed
        |          [--file PATH] [--concurrency N]      events (each verified first). Input is the
        |          [--timeout SECS] [--stop-on-error]   argument, --file, or stdin (also when the
        |                                                arg is `-`), as a single event, JSONL, or
        |                                                a JSON array. Targets default to the
        |                                                account's outbox. --concurrency sets how
        |                                                many events are in flight (default 4);
        |                                                --stop-on-error halts at the first
        |                                                failure instead of finishing the batch.
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
        val concurrency = args.intFlag("concurrency", 4)
        if (concurrency < 1) return Output.error("bad_args", "--concurrency expects a positive number")
        val timeoutSecs = args.timeoutMs(15) / 1000
        val stopOnError = args.bool("stop-on-error")
        val source = RawEventSupport.eventSource(args)
        args.rejectUnknown("relay", "file")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val targets = RawEventSupport.publishTargets(ctx, args)
            if (targets.isEmpty()) {
                return Output.error("no_relays", "no outbox relays configured; pass --relay or run `amy relay add`")
            }

            val outcome =
                PublishBatch(targets, timeoutSecs, stopOnError, concurrency)
                    .run(ctx, RawEventSupport.readEvents(source))

            if (outcome.total == 0) {
                return Output.error("bad_args", "no event JSON on the argument, --file, or stdin")
            }
            // One event in, one event out: keep the historical single-event
            // result shape and exit contract exactly as they were.
            outcome.single?.let { (event, ack) ->
                RawEventSupport.publishGuard(ack, event.id)?.let { return it }
                Output.emit(
                    mapOf("event_id" to event.id, "kind" to event.kind) + RawEventSupport.ackFields(ack),
                )
                return 0
            }
            Output.emit(outcome.asResult())
            return outcome.exitCode()
        }
    }
}
