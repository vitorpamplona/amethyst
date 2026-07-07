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
import com.vitorpamplona.quartz.experimental.clink.client.DebitClient
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit

/**
 * `amy debit …` — CLINK Debits (`ndebit1…`) from the command line, for headless interop
 * testing against a real debit service (e.g. a Lightning.Pub that pre-authorized this
 * account's npub).
 *
 * - `info <ndebit>` decodes a pointer locally (no network).
 * - `pay <ndebit> <bolt11> [--amount SATS]` runs the kind-21002 pay round-trip.
 * - `budget <ndebit> --amount SATS [--frequency day|week|month]` authorizes a budget.
 *
 * Thin assembly only: pointer decode + the request/response events live in `quartz`
 * (`ClinkPointerParser`, `DebitClient`); the round-trip uses `Context.requestResponse`.
 */
object DebitCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "debit",
            tail,
            "debit <info|pay|budget>",
            mapOf(
                "info" to { rest -> info(rest) },
                "pay" to { rest -> pay(dataDir, rest) },
                "budget" to { rest -> budget(dataDir, rest) },
            ),
        )

    /** Local decode of an `ndebit` pointer — no network, no account needed. */
    internal fun info(rest: Array<String>): Int {
        val args = Args(rest)
        val debit =
            ClinkPointerParser.parse(args.positional(0, "ndebit").trim()) as? NDebit
                ?: return Output.error("bad_args", "not a valid ndebit pointer")

        Output.emit(
            mapOf(
                "pubkey" to debit.pubKey,
                "relays" to debit.relays.map { it.url },
                "pointer" to debit.pointer,
                "session" to debit.isSession,
            ),
        )
        return 0
    }

    /** Ask the wallet to pay [bolt11] (kind-21002 round-trip). */
    private suspend fun pay(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val bolt11 = args.positional(1, "bolt11")
        val amount = args.flag("amount")?.toLongOrNull()
        val timeoutMs = args.longFlag("timeout", 15_000)

        return roundTrip(dataDir, args, timeoutMs) { client -> client.payInvoice(bolt11, amount) }
    }

    /** Ask the wallet to authorize a spending budget; omit --frequency for a one-time budget. */
    private suspend fun budget(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val amount =
            args.flag("amount")?.toLongOrNull()
                ?: return Output.error("bad_args", "--amount SATS is required for a budget")
        val frequency = parseFrequency(args.flag("frequency")) ?: return Output.error("bad_args", "unknown --frequency '${args.flag("frequency")}' (day|week|month)")
        val timeoutMs = args.longFlag("timeout", 15_000)

        return roundTrip(dataDir, args, timeoutMs) { client -> client.requestBudget(amount, frequency.value) }
    }

    /**
     * Shared 21002 round-trip: decode the pointer (positional 0), build the request via
     * [buildRequest], publish, await the reply, and emit the preimage or GFY error.
     */
    private suspend fun roundTrip(
        dataDir: DataDir,
        args: Args,
        timeoutMs: Long,
        buildRequest: suspend (DebitClient) -> DebitEvent,
    ): Int {
        val debit =
            ClinkPointerParser.parse(args.positional(0, "ndebit").trim()) as? NDebit
                ?: return Output.error("bad_args", "not a valid ndebit pointer")
        if (debit.relays.isEmpty()) return Output.error("bad_pointer", "ndebit carries no relay to reach")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            return when (val outcome = settle(ctx, debit, timeoutMs, buildRequest)) {
                Settle.Timeout -> {
                    Output.error("timeout", "no response from the debit service within ${timeoutMs}ms")
                    124
                }
                Settle.BadReply -> Output.error("bad_response", "service reply was not a kind-21002 debit event")
                is Settle.Replied -> emitDebit(outcome, debit.pubKey)
            }
        }
    }

    /** Emit a [DebitResponse] as the standard `ok`+preimage success or a structured GFY error. */
    internal fun emitDebit(
        outcome: Settle.Replied,
        servicePubKey: String,
    ): Int {
        val response = outcome.response
        return if (response.isOk()) {
            Output.emit(
                mapOf(
                    "result" to "ok",
                    "preimage" to response.preimage,
                    "request_id" to outcome.requestId,
                    "service" to servicePubKey,
                ),
            )
            0
        } else {
            Output.error(
                "debit_error",
                response.error?.takeIf { it.isNotBlank() } ?: "service returned error code ${response.code}",
                gfyExtra(response),
            )
        }
    }

    /** Structured GFY extras (code + any actionable range/retry_after/delta) for error output. */
    internal fun gfyExtra(response: DebitResponse): Map<String, Any?> =
        mapOf(
            "code" to response.code,
            "range" to response.range?.let { mapOf("min" to it.min, "max" to it.max) },
            "retry_after" to response.retry_after,
            "delta" to response.delta?.let { mapOf("max_delta_ms" to it.max_delta_ms, "actual_delta_ms" to it.actual_delta_ms) },
        )

    /** Result of a single 21002 round-trip, decoupled from how it is emitted. */
    internal sealed interface Settle {
        data class Replied(
            val requestId: String,
            val response: DebitResponse,
        ) : Settle

        data object Timeout : Settle

        data object BadReply : Settle
    }

    /**
     * Core 21002 round-trip against an already-decoded [debit] on an open [ctx]: build the
     * request, publish, await the reply, decrypt. Reused by `debit pay/budget` and by
     * `offer pay` (fetch invoice → settle via debit).
     */
    internal suspend fun settle(
        ctx: Context,
        debit: NDebit,
        timeoutMs: Long,
        buildRequest: suspend (DebitClient) -> DebitEvent,
    ): Settle {
        val client = DebitClient(debit, ctx.signer)
        val requestEvent = buildRequest(client)
        val reply =
            ctx.requestResponse(requestEvent, debit.relays.toSet(), client.responseFilter(requestEvent.id), timeoutMs)
                ?: return Settle.Timeout
        val response = (reply as? DebitEvent)?.let { client.parseResponse(it) } ?: return Settle.BadReply
        return Settle.Replied(requestEvent.id, response)
    }

    /** Parses a `--frequency` value into a one-time (null) or recurring cadence. Null = invalid. */
    internal fun parseFrequency(raw: String?): Frequency? =
        when (raw?.lowercase()) {
            null, "once", "one-time" -> Frequency(null)
            "day", "daily" -> Frequency(DebitFrequency(1, DebitFrequency.UNIT_DAY))
            "week", "weekly" -> Frequency(DebitFrequency(1, DebitFrequency.UNIT_WEEK))
            "month", "monthly" -> Frequency(DebitFrequency(1, DebitFrequency.UNIT_MONTH))
            else -> null
        }

    /** Wrapper so a valid "one-time" budget (null cadence) is distinguishable from an invalid flag. */
    internal data class Frequency(
        val value: DebitFrequency?,
    )
}
