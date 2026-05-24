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
import com.vitorpamplona.amethyst.commons.actions.ZapActions
import com.vitorpamplona.amethyst.commons.services.lnurl.LightningAddressResolver
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import okhttp3.OkHttpClient

/**
 * `amy zap <user|event> <target> <sats>` — build a NIP-57 zap request and
 * fetch a BOLT11 invoice from the recipient's Lightning service.
 *
 * Two subcommands:
 *   * `zap user <user> <sats>` — profile zap (no event reference)
 *   * `zap event <event-id> <sats>` — event zap (must be in local store)
 *
 * The flow is:
 *   1. Resolve recipient identifier → pubkey + kind:0 metadata.
 *   2. Extract LN address (`lud16` preferred, then `lud06` LNURL).
 *   3. Build + sign the NIP-57 kind:9734 zap-request event via
 *      [ZapActions].
 *   4. POST it to the recipient's LNURL-pay callback via
 *      [LightningAddressResolver] to receive a BOLT11 invoice.
 *
 * The invoice is printed but **not** auto-paid — amy has no NWC wallet
 * wired up yet. Paste the invoice into any LN wallet to settle.
 */
object ZapCommand {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "zap <user|event> <target> <sats> [--comment X] [--anon] [--timeout SECS]")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "user" -> zapUser(dataDir, rest)
            "event" -> zapEvent(dataDir, rest)
            else -> Output.error("bad_args", "zap ${tail[0]} — expected user|event")
        }
    }

    private suspend fun zapUser(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "zap user <user> <sats> [--comment X] [--anon] [--timeout SECS]")
        val userArg = rest[0]
        val sats =
            rest[1].toLongOrNull()?.takeIf { it > 0 }
                ?: return Output.error("bad_args", "sats must be a positive integer (got '${rest[1]}')")
        val args = Args(rest.drop(2).toTypedArray())
        val comment = args.flag("comment") ?: ""
        val zapType = parseZapType(args)
        val timeoutMs = args.longFlag("timeout", 8L) * 1000

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val recipient = ctx.requireUserHex(userArg)
            val metadata =
                fetchLatestMetadata(ctx, recipient, ctx.bootstrapRelays(), timeoutMs)
                    ?: return Output.error("not_found", "no kind:0 metadata found for $recipient")
            val lnAddress =
                ZapActions.extractLnAddress(metadata)
                    ?: return Output.error("no_lightning", "recipient has no lud16 or lud06 in their profile")

            val request =
                ZapActions.buildUserZapRequest(
                    signer = ctx.signer,
                    recipientPubkey = recipient,
                    amountMillisats = ZapActions.satsToMillisats(sats),
                    inboxRelays = ctx.outboxRelays(),
                    comment = comment,
                    zapType = zapType,
                )

            emitZapResult(ctx, sats, lnAddress, comment, request, zapType)
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun zapEvent(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "zap event <event-id> <sats> [--comment X] [--anon] [--timeout SECS]")
        val eventId = rest[0]
        if (eventId.length != 64) return Output.error("bad_args", "event-id must be 64-hex (nevent bech32 not yet supported)")
        val sats =
            rest[1].toLongOrNull()?.takeIf { it > 0 }
                ?: return Output.error("bad_args", "sats must be a positive integer (got '${rest[1]}')")
        val args = Args(rest.drop(2).toTypedArray())
        val comment = args.flag("comment") ?: ""
        val zapType = parseZapType(args)
        val timeoutMs = args.longFlag("timeout", 8L) * 1000

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val zappedEvent =
                ctx.store.query<Event>(Filter(ids = listOf(eventId), limit = 1)).firstOrNull()
                    ?: return Output.error("not_found", "event $eventId not in local store; sync first or fetch by id")

            val metadata =
                fetchLatestMetadata(ctx, zappedEvent.pubKey, ctx.bootstrapRelays(), timeoutMs)
                    ?: return Output.error("not_found", "no kind:0 metadata found for author ${zappedEvent.pubKey}")
            val lnAddress =
                ZapActions.extractLnAddress(metadata)
                    ?: return Output.error("no_lightning", "event author has no lud16 or lud06 in their profile")

            val request =
                ZapActions.buildEventZapRequest(
                    signer = ctx.signer,
                    zappedEvent = zappedEvent,
                    amountMillisats = ZapActions.satsToMillisats(sats),
                    inboxRelays = ctx.outboxRelays(),
                    comment = comment,
                    zapType = zapType,
                )

            emitZapResult(ctx, sats, lnAddress, comment, request, zapType, zappedEventId = zappedEvent.id)
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun emitZapResult(
        ctx: Context,
        sats: Long,
        lnAddress: String,
        comment: String,
        request: LnZapRequestEvent,
        zapType: LnZapEvent.ZapType,
        zappedEventId: HexKey? = null,
    ) {
        // Reuse the same OkHttp instance the Context uses for nip-05 / WS;
        // this respects any proxy/timeout config wired in there.
        val resolver = LightningAddressResolver(httpClient = sharedOkHttp(ctx))

        val result =
            resolver.fetchInvoice(
                lnAddress = lnAddress,
                milliSats = ZapActions.satsToMillisats(sats),
                message = comment,
                zapRequest = request,
            )

        when (result) {
            is LightningAddressResolver.Result.Success -> {
                Output.emit(
                    buildMap {
                        put("ln_address", lnAddress)
                        put("amount_sats", sats)
                        put("zap_type", zapType.name.lowercase())
                        put("comment", comment)
                        put("zap_request_id", request.id)
                        if (zappedEventId != null) put("zapped_event_id", zappedEventId)
                        put("invoice", result.invoice)
                    },
                )
            }
            is LightningAddressResolver.Result.Error -> {
                Output.error("invoice_failed", result.message)
            }
        }
    }

    private fun parseZapType(args: Args): LnZapEvent.ZapType =
        when {
            args.bool("anon") -> LnZapEvent.ZapType.ANONYMOUS
            args.bool("private") -> LnZapEvent.ZapType.PRIVATE
            else -> LnZapEvent.ZapType.PUBLIC
        }

    private suspend fun fetchLatestMetadata(
        ctx: Context,
        pubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): MetadataEvent? {
        // Cache-first: try the local store before going to the network.
        ctx.profileOf(pubKey)?.let { return it }
        if (relays.isEmpty()) return null
        val filter = Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubKey), limit = 1)
        val received = ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
        return received
            .mapNotNull { (_, ev) -> ev as? MetadataEvent }
            .filter { it.pubKey == pubKey }
            .maxByOrNull { it.createdAt }
    }

    /**
     * Per-invocation OkHttpClient. Amy's [Context] also has its own OkHttp
     * (for WS + NIP-05); we keep this separate because [Context.okhttp] is
     * private — exposing it just to reuse here would widen the API more
     * than is warranted for a single LNURL fetch.
     */
    private fun sharedOkHttp(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
    ): OkHttpClient = OkHttpClient.Builder().build()
}
