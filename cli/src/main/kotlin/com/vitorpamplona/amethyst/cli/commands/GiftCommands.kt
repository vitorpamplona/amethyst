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
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * `amy gift wrap --to USER [EVENT-JSON]` and `amy gift unwrap [GIFTWRAP-JSON]`
 * — the NIP-59 gift-wrap primitive (nak's `gift`).
 *
 *   wrap   seals a signed inner event for USER and wraps it in a kind:1059
 *          gift wrap (random ephemeral sender). Prints the wrap; pass
 *          `--relay URL[,URL…]` to also broadcast it.
 *   unwrap decrypts a kind:1059 gift wrap with the active account's key,
 *          unseals it, and prints the inner event.
 *
 * Inner/wrap JSON comes from the positional argument or stdin (`-`).
 *
 * Thin assembly only: seal/wrap/unwrap all live in quartz
 * (`SealedRumorEvent`, `GiftWrapEvent`).
 */
object GiftCommands {
    val USAGE: String =
        """
        |amy gift — NIP-59 gift-wrap primitives (inner/wrap JSON from arg or stdin/`-`)
        |
        |  gift wrap --to USER [EVENT-JSON]            seal + wrap a signed inner event for
        |       [--relay URL[,URL…]]                    USER (add --relay to broadcast the wrap)
        |  gift unwrap [GIFTWRAP-JSON]                 decrypt + unseal a kind:1059 wrap addressed
        |                                               to the active account
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "gift",
            tail,
            "gift <wrap|unwrap>",
            mapOf(
                "wrap" to { rest -> wrap(dataDir, rest) },
                "unwrap" to { rest -> unwrap(dataDir, rest) },
            ),
            help = USAGE,
        )

    private suspend fun wrap(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val to = args.flag("to") ?: return Output.error("bad_args", "gift wrap requires --to USER")
        val json = RawEventSupport.readArgOrStdin(args)
        if (json.isEmpty()) return Output.error("bad_args", "no inner event JSON on the argument or stdin")
        val inner =
            try {
                Event.fromJson(json)
            } catch (e: Exception) {
                return Output.error("bad_args", "could not parse inner event JSON: ${e.message}")
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val peer = ctx.requireUserHex(to)
            val seal = SealedRumorEvent.create(event = inner, encryptTo = peer, signer = ctx.signer)
            val giftWrap = GiftWrapEvent.create(event = seal, recipientPubKey = peer)
            val wrapNode = Output.mapper.readTree(giftWrap.toJson())

            val targets = RawEventSupport.relayFlag(args)
            args.rejectUnknown()
            if (targets.isEmpty()) {
                Output.emit(mapOf("event" to wrapNode, "published" to false))
                return 0
            }
            val ack = ctx.publish(giftWrap, targets)
            RawEventSupport.publishGuard(ack, giftWrap.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event" to wrapNode,
                    "published" to true,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun unwrap(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        args.rejectUnknown()
        val json = RawEventSupport.readArgOrStdin(args)
        if (json.isEmpty()) return Output.error("bad_args", "no gift wrap JSON on the argument or stdin")
        val giftWrap =
            try {
                Event.fromJson(json) as? GiftWrapEvent
                    ?: return Output.error("bad_args", "not a kind:1059 gift wrap event")
            } catch (e: Exception) {
                return Output.error("bad_args", "could not parse gift wrap JSON: ${e.message}")
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val unwrapped =
                try {
                    giftWrap.unwrapThrowing(ctx.signer)
                } catch (e: Exception) {
                    return Output.error("decrypt_failed", "could not unwrap (is this gift addressed to the active account?): ${e.message}")
                }
            // The wrap holds a seal (kind:13); unseal it to recover the rumor.
            val inner = if (unwrapped is SealedRumorEvent) unwrapped.unsealThrowing(ctx.signer) else unwrapped
            Output.emit(mapOf("event" to Output.mapper.readTree(inner.toJson())))
            return 0
        }
    }
}
