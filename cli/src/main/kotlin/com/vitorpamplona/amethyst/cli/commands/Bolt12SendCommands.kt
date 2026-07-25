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
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipXXBolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipXXBolt12Zaps.verify.Bolt12ZapValidation

/**
 * The BOLT12 send-side sub-verbs of `amy bolt12` (split from [Bolt12Commands] to keep
 * each file focused). Two steps because amy has no NWC rail to fetch the proof itself:
 * [intent] signs the 9737 and prints the `payer_note`; the caller pays the offer
 * out-of-band with that note; [zap] wraps the same intent + settled proof into a 9736.
 */
object Bolt12SendCommands {
    suspend fun intent(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val eventMode = rest.firstOrNull() == "event"
        val body = if (eventMode) rest.drop(1).toTypedArray() else rest
        if (body.size < 2) {
            return Output.error("bad_args", "bolt12 intent [event] <user|event-id> <sats> --offer <lno1> [--comment X]")
        }
        val target = body[0]
        val sats =
            body[1].toLongOrNull()?.takeIf { it > 0 }
                ?: return Output.error("bad_args", "sats must be a positive integer (got '${body[1]}')")
        val args = Args(body.drop(2).toTypedArray())
        val offerArg = args.flag("offer") ?: return Output.error("bad_args", "--offer <lno1…> is required")
        val offer = Bolt12ZapActions.canonicalOfferOrNull(offerArg) ?: return Output.error("bad_args", "--offer is not a valid BOLT12 offer")
        val comment = args.flag("comment") ?: ""
        args.rejectUnknown()
        val amountMsat = Bolt12ZapActions.satsToMillisats(sats)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val intent =
                if (eventMode) {
                    if (target.length != 64) return Output.error("bad_args", "event-id must be 64-hex")
                    val zappedEvent =
                        ctx.store.query<Event>(Filter(ids = listOf(target), limit = 1)).firstOrNull()
                            ?: return Output.error("not_found", "event $target not in local store; sync or fetch first")
                    val recipient = zappedEvent.pubKey
                    Bolt12ZapActions.buildEventIntent(ctx.signer, recipient, amountMsat, offer, zappedEvent, comment)
                } else {
                    val recipient = ctx.requireUserHex(target)
                    Bolt12ZapActions.buildProfileIntent(ctx.signer, recipient, amountMsat, offer, comment)
                }

            Output.emit(
                mapOf(
                    "intent_id" to intent.id,
                    "recipient" to intent.recipient(),
                    "amount_msat" to amountMsat,
                    "offer" to offer,
                    // Put this in the BOLT12 invoice request's invreq_payer_note when paying.
                    "payer_note" to Bolt12ZapActions.payerNote(intent),
                    // Feed this verbatim back to `bolt12 zap --intent` once you have the proof.
                    "intent_json" to intent.toJson(),
                ),
            )
            return 0
        }
    }

    suspend fun zap(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val intentJson = args.flag("intent") ?: return Output.error("bad_args", "bolt12 zap --intent <json> --proof <lnp1>")
        val proofArg = args.flag("proof") ?: return Output.error("bad_args", "--proof <lnp1…> is required")
        args.rejectUnknown()

        val proof = Bolt12Bech32.canonicalize(proofArg)
        if (!Bolt12Bech32.isPayerProof(proof)) return Output.error("bad_args", "--proof is not a BOLT12 payer proof (lnp1…)")

        val intent =
            runCatching { Event.fromJson(intentJson) as? Bolt12ZapIntentEvent }.getOrNull()
                ?: return Output.error("bad_args", "--intent is not a valid kind:9737 zap-intent event")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // Sign the 9736 with our account key. The validator requires the zap and the
            // embedded intent to share a pubkey, so an intent signed by another key is
            // rejected below and nothing is published.
            val zap = Bolt12ZapActions.buildZap(ctx.signer, intent, proof, anonymous = false)

            when (val result = Bolt12ZapActions.validate(zap)) {
                is Bolt12ZapValidation.Invalid ->
                    return Output.error("invalid_zap", "assembled zap failed validation: ${result.reason.name}; nothing published")

                is Bolt12ZapValidation.Valid -> {
                    val recipientInbox: Set<NormalizedRelayUrl> =
                        ctx
                            .relaysOf(result.recipient)
                            ?.readRelaysNorm()
                            ?.toSet()
                            .orEmpty()
                    val relays = ctx.outboxRelays() + recipientInbox
                    val results = ctx.publish(zap, relays)
                    Output.emit(
                        mapOf(
                            "event_id" to zap.id,
                            "recipient" to result.recipient,
                            "amount_msat" to result.amountMillisats,
                            "payment_hash" to result.paymentHashHex,
                            "crypto_verified" to result.proofCryptoVerified,
                            "published_to" to results.keys.map { it.url },
                            "accepted_by" to results.filterValues { it.accepted }.keys.map { it.url },
                        ),
                    )
                    return 0
                }
            }
        }
    }
}
