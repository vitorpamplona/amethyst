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
import com.vitorpamplona.quartz.experimental.clink.client.OfferClient
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer

/**
 * `amy offer …` — CLINK Offers (`noffer1…`) from the command line, for headless interop
 * testing against a real offer service.
 *
 * - `info <noffer>` decodes a pointer locally (no network).
 * - `request <noffer> [--amount N] [--timeout MS]` runs the kind-21001 round-trip:
 *   publishes the request to the pointer's relays and prints the returned BOLT-11.
 *
 * Thin assembly only: pointer decode + the request/response event live in `quartz`
 * (`ClinkPointerParser`, `OfferClient`); the relay round-trip uses `Context.requestResponse`.
 */
object OfferCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "offer <info|request>")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "info" -> info(rest)
            "request" -> request(dataDir, rest)
            else -> Output.error("bad_args", "offer ${tail[0]} (expected info|request)")
        }
    }

    /** Local decode of a `noffer` pointer — no network, no account needed. */
    private fun info(rest: Array<String>): Int {
        val args = Args(rest)
        val offer =
            ClinkPointerParser.parse(args.positional(0, "noffer").trim()) as? NOffer
                ?: return Output.error("bad_args", "not a valid noffer pointer")

        Output.emit(
            mapOf(
                "pubkey" to offer.pubKey,
                "relays" to offer.relays.map { it.url },
                "pointer" to offer.pointer,
                "price_type" to offer.priceType?.name?.lowercase(),
                "price_sats" to offer.price,
            ),
        )
        return 0
    }

    /** Request a fresh BOLT-11 from the offer service (kind-21001 round-trip). */
    private suspend fun request(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val amount = args.flag("amount")?.toLongOrNull()
        val timeoutMs = args.longFlag("timeout", 15_000)

        val offer =
            ClinkPointerParser.parse(args.positional(0, "noffer").trim()) as? NOffer
                ?: return Output.error("bad_args", "not a valid noffer pointer")
        val relays = offer.relays.toSet()
        if (relays.isEmpty()) return Output.error("bad_pointer", "noffer carries no relay to reach")

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val client = OfferClient(offer, ctx.signer)
            val requestEvent = client.requestInvoice(amountSats = amount)

            val reply = ctx.requestResponse(requestEvent, relays, client.responseFilter(requestEvent.id), timeoutMs)
            if (reply == null) {
                Output.error("timeout", "no response from the offer service within ${timeoutMs}ms")
                return 124
            }

            val response =
                (reply as? OfferEvent)?.let { client.parseResponse(it) }
                    ?: return Output.error("bad_response", "service reply was not a kind-21001 offer event")

            return if (response.isSuccess()) {
                Output.emit(
                    mapOf(
                        "bolt11" to response.bolt11,
                        "request_id" to requestEvent.id,
                        "service" to offer.pubKey,
                    ),
                )
                0
            } else {
                Output.error(
                    "offer_error",
                    response.error?.takeIf { it.isNotBlank() } ?: "service returned error code ${response.code}",
                )
            }
        } finally {
            ctx.close()
        }
    }
}
