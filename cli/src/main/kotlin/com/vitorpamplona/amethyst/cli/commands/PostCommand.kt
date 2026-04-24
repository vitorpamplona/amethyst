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
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

/**
 * `amy post <text> [--relay URL …]` — publish a NIP-10 kind:1 short text note
 * to the user's outbox relays.
 *
 * Threading is intentionally out of scope here — `amy post` only handles new
 * top-level notes. Replies/quotes need richer event-hint plumbing and will get
 * their own verb when needed.
 */
object PostCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "post <text> [--relay URL …]")
        val text = rest[0]
        if (text.isBlank()) return Json.error("bad_args", "post text must not be blank")

        val args = Args(rest.drop(1).toTypedArray())
        val extraRelays =
            args.flags["relay"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList()

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val outbox = ctx.outboxRelays()
            val extraNormalized =
                extraRelays.mapNotNull {
                    com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                        .normalizeOrNull(it)
                }
            val targets = (outbox + extraNormalized).toSet()
            if (targets.isEmpty()) {
                return Json.error("no_relays", "no outbox relays configured; pass --relay or run `amy relay add`")
            }

            val signed = ctx.signer.sign(TextNoteEvent.build(text))
            val ack = ctx.publish(signed, targets)

            Json.writeLine(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "created_at" to signed.createdAt,
                    "content" to signed.content,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
