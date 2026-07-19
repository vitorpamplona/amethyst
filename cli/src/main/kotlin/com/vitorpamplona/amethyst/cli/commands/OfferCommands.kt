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
import com.vitorpamplona.quartz.experimental.clink.offers.OfferErrorCode
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id

/**
 * `amy offer …` — CLINK Offers (`noffer1…`) from the command line, for headless interop
 * testing against a real offer service.
 *
 * - `info <noffer>` decodes a pointer locally (no network).
 * - `discover <nip05>` resolves a profile's advertised offer from its NIP-05 `.well-known`.
 * - `request <noffer> [--amount N] [--timeout SECS] [--follow] [--payer-data k=v,…]` runs the
 *   kind-21001 round-trip: publishes the request to the pointer's relays and prints the
 *   returned BOLT-11. With `--follow` it chases an "Expired or Moved" (code 3) reply to the
 *   `latest` pointer. `--payer-data` attaches the payer fields some offers require.
 * - `pay <noffer> --with <ndebit> [--amount N]` fetches the invoice and settles it end-to-end
 *   through a CLINK debit pointer (offer round-trip → debit round-trip).
 *
 * Thin assembly only: pointer decode + the request/response events live in `quartz`
 * (`ClinkPointerParser`, `OfferClient`, `DebitClient`); the relay round-trips use
 * `Context.requestResponse` (debit settlement is shared with [DebitCommands]).
 */
object OfferCommands {
    private const val MAX_FOLLOW_HOPS = 3
    private const val ERR_NOT_A_NOFFER = "not a valid noffer pointer"

    val USAGE: String =
        """
        |CLINK Offers:
        |  offer info NOFFER                          decode a noffer1… pointer (local, no network)
        |  offer discover NIP05                       resolve a profile's advertised offer from its
        |                                              NIP-05 .well-known (e.g. bob@example.com)
        |  offer request NOFFER [--amount SATS]       kind:21001 round-trip: ask the service for a
        |    [--timeout SECS] [--follow]                fresh BOLT11 (amount required for spontaneous
        |    [--payer-data k=v[,k=v…]]                  offers; --follow chases an Expired-or-Moved
        |                                              reply; --payer-data attaches required fields)
        |  offer pay NOFFER --with NDEBIT             fetch the invoice and settle it end-to-end
        |    [--amount SATS] [--timeout SECS]           through a CLINK debit pointer (kind:21002)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "offer",
            tail,
            "offer <info|discover|request|pay>",
            help = USAGE,
            routes =
                mapOf(
                    "info" to { rest -> info(rest) },
                    "discover" to { rest -> discover(dataDir, rest) },
                    "request" to { rest -> request(dataDir, rest) },
                    "pay" to { rest -> pay(dataDir, rest) },
                ),
        )

    /**
     * Resolve a profile's advertised offer from its NIP-05 `.well-known/nostr.json` `clink_offer`
     * (the app's discovery fallback). A profile's kind-0 `clink_offer` is readable via
     * `amy profile show <user>`.
     */
    private suspend fun discover(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        args.rejectUnknown()
        val id =
            Nip05Id.parse(args.positional(0, "nip05").trim())
                ?: return Output.error("bad_args", "not a valid NIP-05 address (e.g. bob@example.com)")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val noffer = ctx.nip05Client.loadClinkOffer(id)
            if (noffer == null) {
                Output.emit(mapOf("nip05" to id.toDisplayValue(), "found" to false))
                return 0
            }
            val offer = ClinkPointerParser.parse(noffer) as? NOffer
            Output.emit(
                mapOf(
                    "nip05" to id.toDisplayValue(),
                    "found" to true,
                    "noffer" to noffer,
                    "pubkey" to offer?.pubKey,
                    "relays" to offer?.relays?.map { it.url },
                    "pointer" to offer?.pointer,
                    "price_type" to offer?.priceType?.name?.lowercase(),
                    "price_sats" to offer?.price,
                ),
            )
            return 0
        }
    }

    /** Local decode of a `noffer` pointer — no network, no account needed. */
    internal fun info(rest: Array<String>): Int {
        val args = Args(rest)
        args.rejectUnknown()
        val offer =
            ClinkPointerParser.parse(args.positional(0, "noffer").trim()) as? NOffer
                ?: return Output.error("bad_args", ERR_NOT_A_NOFFER)

        Output.emit(
            mapOf(
                "pubkey" to offer.pubKey,
                "relays" to offer.relays.map { it.url },
                "pointer" to offer.pointer,
                "price_type" to offer.priceType.name.lowercase(),
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
        val timeoutMs = args.timeoutMs(15)
        // Guard ms-era scripts: a value this large is almost certainly milliseconds.
        if (timeoutMs > 3_600_000) return Output.error("bad_args", "--timeout is seconds (max 3600); ${timeoutMs / 1000} looks like milliseconds")
        val follow = args.bool("follow")
        // Offers can be configured to require payer fields (e.g. email); Lightning.Pub
        // rejects a request missing them as "Invalid Offer" (code 1), so the round-trip
        // is untestable against such offers without a way to attach them.
        val payerData: Map<String, Any?>? =
            args.flag("payer-data")?.split(',')?.associate { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return Output.error("bad_args", "--payer-data expects key=value[,key2=value2…]")
                pair.take(idx) to pair.substring(idx + 1)
            }
        args.rejectUnknown()

        var offer =
            ClinkPointerParser.parse(args.positional(0, "noffer").trim()) as? NOffer
                ?: return Output.error("bad_args", ERR_NOT_A_NOFFER)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            var hops = 0
            while (hops <= MAX_FOLLOW_HOPS) {
                val relays = offer.relays.toSet()
                if (relays.isEmpty()) return Output.error("bad_pointer", "noffer carries no relay to reach")

                val client = OfferClient(offer, ctx.signer)
                val requestEvent = client.requestInvoice(amountSats = amount, payerData = payerData)

                val reply = ctx.requestResponse(requestEvent, relays, client.responseFilter(requestEvent.id), timeoutMs)
                if (reply == null) {
                    Output.error("timeout", "no response from the offer service within ${timeoutMs / 1000}s")
                    return 124
                }

                val response =
                    (reply as? OfferEvent)?.let { client.parseResponse(it) }
                        ?: return Output.error("bad_response", "service reply was not a kind-21001 offer event")

                if (response.isSuccess()) {
                    Output.emit(
                        mapOf(
                            "bolt11" to response.bolt11,
                            "request_id" to requestEvent.id,
                            "service" to offer.pubKey,
                            "followed_hops" to hops,
                        ),
                    )
                    return 0
                }

                // "Expired or Moved" (code 3) may carry a replacement `noffer`; chase it on --follow.
                val moved = response.latest?.let { ClinkPointerParser.parse(it) as? NOffer }
                if (follow && response.code == OfferErrorCode.EXPIRED_OR_MOVED && moved != null) {
                    offer = moved
                    hops++
                    continue
                }

                return Output.error(
                    "offer_error",
                    response.error?.takeIf { it.isNotBlank() } ?: "service returned error code ${response.code}",
                    offerErrorExtra(response),
                )
            }
            return Output.error("offer_error", "too many redirects following moved offers (>$MAX_FOLLOW_HOPS)")
        }
    }

    /**
     * Pay an offer end-to-end: fetch a fresh BOLT-11 (kind-21001) and settle it through a
     * CLINK debit pointer (kind-21002). The CLI is stateless, so the funding source is given
     * explicitly with `--with <ndebit>`.
     */
    private suspend fun pay(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val amount = args.flag("amount")?.toLongOrNull()
        val timeoutMs = args.timeoutMs(15)
        // Guard ms-era scripts: a value this large is almost certainly milliseconds.
        if (timeoutMs > 3_600_000) return Output.error("bad_args", "--timeout is seconds (max 3600); ${timeoutMs / 1000} looks like milliseconds")

        val offer =
            ClinkPointerParser.parse(args.positional(0, "noffer").trim()) as? NOffer
                ?: return Output.error("bad_args", ERR_NOT_A_NOFFER)
        val withFlag =
            args.flag("with")
                ?: return Output.error("bad_args", "offer pay needs --with <ndebit> to settle the fetched invoice")
        val debit =
            ClinkPointerParser.parse(withFlag.trim()) as? NDebit
                ?: return Output.error("bad_args", "--with is not a valid ndebit pointer")
        args.rejectUnknown()

        val offerRelays = offer.relays.toSet()
        if (offerRelays.isEmpty()) return Output.error("bad_pointer", "noffer carries no relay to reach")
        if (debit.relays.isEmpty()) return Output.error("bad_pointer", "ndebit carries no relay to reach")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            // 1. fetch a fresh BOLT-11 from the offer service.
            val offerClient = OfferClient(offer, ctx.signer)
            val offerReq = offerClient.requestInvoice(amountSats = amount)
            val offerReply = ctx.requestResponse(offerReq, offerRelays, offerClient.responseFilter(offerReq.id), timeoutMs)
            if (offerReply == null) {
                Output.error("timeout", "no response from the offer service within ${timeoutMs / 1000}s")
                return 124
            }
            val offerResp =
                (offerReply as? OfferEvent)?.let { offerClient.parseResponse(it) }
                    ?: return Output.error("bad_response", "offer reply was not a kind-21001 event")
            if (!offerResp.isSuccess()) {
                return Output.error(
                    "offer_error",
                    offerResp.error?.takeIf { it.isNotBlank() } ?: "service returned error code ${offerResp.code}",
                    offerErrorExtra(offerResp),
                )
            }
            val bolt11 =
                offerResp.bolt11
                    ?: return Output.error("bad_response", "offer succeeded but returned no bolt11")

            // 2. settle the invoice through the debit service (shared with `debit pay`).
            return when (val outcome = DebitCommands.settle(ctx, debit, timeoutMs) { it.payInvoice(bolt11, amount) }) {
                DebitCommands.Settle.Timeout -> {
                    Output.error("timeout", "no response from the debit service within ${timeoutMs / 1000}s")
                    124
                }
                DebitCommands.Settle.BadReply -> Output.error("bad_response", "debit reply was not a kind-21002 event")
                is DebitCommands.Settle.Replied ->
                    if (outcome.response.isOk()) {
                        Output.emit(
                            mapOf(
                                "result" to "ok",
                                "preimage" to outcome.response.preimage,
                                "bolt11" to bolt11,
                                "offer_request_id" to offerReq.id,
                                "debit_request_id" to outcome.requestId,
                                "offer_service" to offer.pubKey,
                                "debit_service" to debit.pubKey,
                            ),
                        )
                        0
                    } else {
                        Output.error(
                            "debit_error",
                            outcome.response.error?.takeIf { it.isNotBlank() } ?: "service returned error code ${outcome.response.code}",
                            DebitCommands.gfyExtra(outcome.response),
                        )
                    }
            }
        }
    }

    /** Structured offer-error extras (code + moved `latest` pointer + acceptable range). */
    private fun offerErrorExtra(response: OfferResponse): Map<String, Any?> =
        mapOf(
            "code" to response.code,
            "latest" to response.latest,
            "range" to response.range?.let { mapOf("min" to it.min, "max" to it.max) },
        )
}
