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
import com.vitorpamplona.amethyst.commons.actions.Bolt12ZapActions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify.Bolt12ZapValidation
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent

/**
 * `amy bolt12 …` — NIP-B1 BOLT12 zaps from the command line, for headless interop
 * testing of the quartz BOLT12 stack.
 *
 * Sub-verbs:
 *   * `bolt12 decode <lno1…|lnp1…>` — decode an offer or payer proof to its fields.
 *   * `bolt12 verify <event-id>`    — validate a kind:9736 zap in the local store.
 *   * `bolt12 offer get <user>`     — fetch + show a user's kind:10058 offers.
 *   * `bolt12 offer set <lno1…>…`   — publish your own kind:10058 offer list.
 *   * `bolt12 zap|intent …`         — send-side assembly (see [Bolt12SendCommands]).
 *
 * amy has no NIP-47/NWC payment rail, so BOLT12 sending is a two-step, out-of-band
 * flow: `bolt12 intent` signs the 9737 and prints its `payer_note`; you pay the offer
 * elsewhere putting that note in the invoice request's `invreq_payer_note`; then
 * `bolt12 zap --intent <json> --proof <lnp1>` wraps that same signed intent and the
 * settled proof into a kind:9736. (The intent must be reused verbatim — re-deriving it
 * would change its id, and the `payer_note` you paid with wouldn't match.)
 */
object Bolt12Commands {
    val USAGE: String =
        """
        |BOLT12 zaps (NIP-B1):
        |  bolt12 decode LNO1|LNP1            decode an offer or payer proof to fields
        |  bolt12 verify EVENT-ID             validate a kind:9736 zap in the local store
        |  bolt12 offer get USER              fetch + show a user's kind:10058 offers
        |    [--timeout SECS]
        |  bolt12 offer set LNO1 [LNO1 …]     publish your own kind:10058 offer list
        |  bolt12 intent USER SATS --offer LNO1 [--comment X]
        |  bolt12 intent event EVENT-ID SATS --offer LNO1 [--comment X]
        |                                     sign a 9737 intent; prints its id + payer_note
        |  bolt12 zap --intent JSON --proof LNP1
        |                                     wrap a signed intent + settled proof into a
        |                                     kind:9736, validate, and publish
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "bolt12",
            tail,
            "bolt12 <decode|verify|offer|intent|zap> …",
            help = USAGE,
            routes =
                mapOf(
                    "decode" to { rest -> decode(rest) },
                    "verify" to { rest -> verify(dataDir, rest) },
                    "offer" to { rest -> offerDispatch(dataDir, rest) },
                    "intent" to { rest -> Bolt12SendCommands.intent(dataDir, rest) },
                    "zap" to { rest -> Bolt12SendCommands.zap(dataDir, rest) },
                ),
        )

    private fun decode(rest: Array<String>): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "bolt12 decode <lno1…|lnp1…>")
        val raw = rest[0]
        val canonical = Bolt12Bech32.canonicalize(raw)
        when {
            Bolt12Bech32.isOffer(canonical) -> {
                val fields = Bolt12ZapActions.decodeOffer(raw) ?: return Output.error("decode_failed", "not a parseable BOLT12 offer")
                Output.emit(mapOf("type" to "offer") + fields)
            }
            Bolt12Bech32.isPayerProof(canonical) -> {
                val fields = Bolt12ZapActions.decodeProof(raw) ?: return Output.error("decode_failed", "not a parseable BOLT12 payer proof")
                Output.emit(mapOf("type" to "payer_proof") + fields)
            }
            else -> return Output.error("bad_args", "not a BOLT12 offer (lno1…) or payer proof (lnp1…)")
        }
        return 0
    }

    private suspend fun verify(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "bolt12 verify <event-id>")
        val eventId = rest[0]
        if (eventId.length != 64) return Output.error("bad_args", "event-id must be 64-hex")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // Constrain by kind AND cast defensively: the store filters by id alone and
            // returns whatever kind that id actually is, so querying <Bolt12ZapEvent>
            // directly would ClassCastException if the id points at a non-9736 event.
            val zap =
                ctx.store
                    .query<Event>(Filter(kinds = listOf(Bolt12ZapEvent.KIND), ids = listOf(eventId), limit = 1))
                    .firstOrNull() as? Bolt12ZapEvent
                    ?: return Output.error("not_found", "no kind:9736 event $eventId in local store; sync or fetch first")

            when (val result = Bolt12ZapActions.validate(zap)) {
                is Bolt12ZapValidation.Valid ->
                    Output.emit(
                        buildMap {
                            put("event_id", zap.id)
                            put("valid", true)
                            put("crypto_verified", result.proofCryptoVerified)
                            put("recipient", result.recipient)
                            result.payer?.let { put("payer", it) }
                            put("anonymous", result.payer == null)
                            put("amount_msat", result.amountMillisats)
                            put("payment_hash", result.paymentHashHex)
                            result.zappedEventId?.let { put("zapped_event_id", it) }
                        },
                    )

                is Bolt12ZapValidation.Invalid ->
                    Output.emit(mapOf("event_id" to zap.id, "valid" to false, "reason" to result.reason.name))
            }
            return 0
        }
    }

    private suspend fun offerDispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "bolt12 offer",
            tail,
            "bolt12 offer <get|set> …",
            help = USAGE,
            routes =
                mapOf(
                    "get" to { rest -> offerGet(dataDir, rest) },
                    "set" to { rest -> offerSet(dataDir, rest) },
                ),
        )

    private suspend fun offerGet(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "bolt12 offer get <user> [--timeout SECS]")
        val args = Args(rest.drop(1).toTypedArray())
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val user = ctx.requireUserHex(rest[0])
            val relays = ctx.bootstrapRelays()
            val filter = Filter(kinds = listOf(Bolt12OfferListEvent.KIND), authors = listOf(user), limit = 1)
            val latest =
                (ctx.store.query<Bolt12OfferListEvent>(filter) + ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs).mapNotNull { it.second as? Bolt12OfferListEvent })
                    .filter { it.pubKey == user }
                    .maxByOrNull { it.createdAt }

            Output.emit(mapOf("user" to user, "offers" to (latest?.offers() ?: emptyList())))
            return 0
        }
    }

    private suspend fun offerSet(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "bolt12 offer set <lno1…> [<lno1…> …]")
        val canonical =
            rest.map { raw ->
                Bolt12ZapActions.canonicalOfferOrNull(raw) ?: return Output.error("bad_args", "not a valid BOLT12 offer: $raw")
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val event = Bolt12ZapActions.buildOfferList(ctx.signer, canonical)
            val relays: Set<NormalizedRelayUrl> = ctx.outboxRelays()
            val results = ctx.publish(event, relays)
            Output.emit(
                mapOf(
                    "event_id" to event.id,
                    "offers" to canonical,
                    "published_to" to results.keys.map { it.url },
                    "accepted_by" to results.filterValues { it.accepted }.keys.map { it.url },
                ),
            )
            return 0
        }
    }
}
