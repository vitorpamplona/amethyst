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

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy event --kind N [--content TEXT] [--tags JSON] [--created-at TS]
 *            [--publish] [--relay URL[,URL…]]` — build and sign an arbitrary
 * Nostr event (nak's `event`).
 *
 * Signs with the active account. By default it only prints the signed event
 * (no relay traffic). Pass `--publish` to broadcast to the account's outbox,
 * or `--relay` to broadcast to a specific set (implies publish).
 *
 * `--tags` takes a JSON array-of-arrays, e.g.
 *   --tags '[["p","<hex>"],["t","nostr"],["e","<id>","","root"]]'
 *
 * Thin assembly only: event construction + signing live in quartz
 * (`EventTemplate` / `NostrSigner.sign`); this file parses flags.
 */
object EventCommand {
    val USAGE: String =
        """
        |amy event — build + sign an arbitrary event with the active account
        |
        |  event --kind N [--content TEXT]             prints the signed event; add --publish
        |        [--tags JSON] [--created-at TS]        (or --relay) to broadcast. --tags takes a
        |        [--publish] [--relay URL[,URL…]]       JSON array-of-arrays, e.g. '[["t","nostr"]]'.
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
        val kind =
            args.flag("kind")?.toIntOrNull()
                ?: return Output.error("bad_args", "event requires --kind N")
        val content = args.flag("content", "") ?: ""
        val createdAt = args.flag("created-at")?.toLongOrNull() ?: TimeUtils.now()

        val tags: Array<Array<String>> =
            try {
                args
                    .flag("tags")
                    ?.let { Output.mapper.readValue<List<List<String>>>(it) }
                    ?.map { it.toTypedArray() }
                    ?.toTypedArray()
                    ?: emptyArray()
            } catch (e: Exception) {
                return Output.error("bad_args", "--tags must be a JSON array of string arrays: ${e.message}")
            }

        // Publish when explicitly asked (--publish) or when a relay set is given.
        val wantPublish = args.bool("publish") || args.flag("relay") != null
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed: Event = ctx.signer.sign(createdAt, kind, tags, content)
            val eventNode = Output.mapper.readTree(signed.toJson())

            if (!wantPublish) {
                Output.emit(mapOf("event" to eventNode, "published" to false))
                return 0
            }

            val targets = RawEventSupport.publishTargets(ctx, args)
            if (targets.isEmpty()) {
                return Output.error("no_relays", "no outbox relays configured; pass --relay or run `amy relay add`")
            }
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event" to eventNode,
                    "published" to true,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }
}
