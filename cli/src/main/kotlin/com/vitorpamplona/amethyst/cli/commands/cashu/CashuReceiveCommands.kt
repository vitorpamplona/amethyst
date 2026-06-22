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
package com.vitorpamplona.amethyst.cli.commands.cashu

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.commands.route
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenB64Parser

/**
 * `amy cashu receive <ln|complete|resume|token|nutzap-sweep>` — inbound flows,
 * all on the shared commons CashuWalletOps the Android wallet runs.
 */
object CashuReceiveCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu receive",
            tail = tail,
            usage = "cashu receive <ln|complete|resume|token|nutzap-sweep>",
            routes =
                mapOf(
                    "ln" to { rest -> ln(dataDir, rest) },
                    "complete" to { rest -> complete(dataDir, rest) },
                    "resume" to { rest -> complete(dataDir, rest) },
                    "token" to { rest -> token(dataDir, rest) },
                    "nutzap-sweep" to { rest -> nutzapSweep(dataDir, rest) },
                ),
        )

    /** Resolve the mint to use: --mint, else the wallet's first configured mint. */
    private fun pickMint(
        flag: String?,
        mints: List<String>,
    ): String? = flag?.trimEnd('/') ?: mints.firstOrNull()

    private suspend fun ln(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val sats = args.positional(0, "sats").toLongOrNull() ?: return Output.error("bad_args", "sats must be a positive integer")
        if (sats <= 0) return Output.error("bad_args", "sats must be positive")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val mint = pickMint(args.flag("mint"), ctx.cashuSnapshot().mints) ?: return Output.error("no_mint", "no mint configured; pass --mint URL")
            return try {
                val started = ctx.cashuOps().startMintFromLightning(mint, sats)
                Output.emit(
                    mapOf(
                        "quote_id" to started.mintQuote.quote,
                        "invoice" to started.invoice,
                        "mint_url" to mint,
                        "amount_sats" to sats,
                        "kind_7374_event_id" to started.quoteEvent.id,
                    ),
                )
                0
            } catch (e: Exception) {
                Output.error("mint_unreachable", describe(e))
            }
        }
    }

    private suspend fun complete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val quoteId = Args(rest).positional(0, "quote-id")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val snap = ctx.cashuSnapshot()
            val quoteEvent =
                snap.pendingQuotes.firstOrNull { runCatching { it.quoteId(ctx.signer) }.getOrNull() == quoteId }
                    ?: return Output.error("mint_quote_gone", "no pending kind:7374 quote with id $quoteId")
            val mint = quoteEvent.mint() ?: snap.mints.firstOrNull() ?: return Output.error("no_mint", "quote has no mint")
            return try {
                // Poll the mint: a quote that isn't settled yet can't be minted.
                val status = ctx.cashuOps().checkMintQuote(mint, quoteId)
                if (!status.isSettled()) {
                    Output.emit(mapOf("status" to "pending", "quote_id" to quoteId, "mint_url" to mint))
                    return 0
                }
                // The kind:7374 stores only the quote id; recover the mint amount
                // from the quote's bolt11 invoice (what the mint settled).
                val amount = LnInvoiceUtil.getAmountInSats(status.request).toLong()
                val done = ctx.cashuOps().completeMintFromLightning(mint, quoteEvent, amount)
                Output.emit(
                    mapOf(
                        "status" to "paid",
                        "amount_sats" to done.mintedAmount,
                        "token_event_id" to done.tokenEvent.id,
                        "history_event_id" to done.historyEvent.id,
                    ),
                )
                0
            } catch (e: Exception) {
                Output.error("mint_unreachable", describe(e))
            }
        }
    }

    private suspend fun token(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val raw = Args(rest).positional(0, "token").trim()
        val parsed = CashuTokenB64Parser.parse(raw) ?: return Output.error("bad_args", "could not parse cashu token")
        if (parsed.isEmpty()) return Output.error("bad_args", "token has no proofs")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            return try {
                var total = 0L
                var lastTokenEventId: String? = null
                var lastHistoryEventId: String? = null
                var mint = ""
                for (t in parsed) {
                    val redeemed = ctx.cashuOps().redeemToken(raw, t.proofs, t.mint)
                    total += redeemed.amount
                    lastTokenEventId = redeemed.tokenEvent.id
                    lastHistoryEventId = redeemed.historyEvent.id
                    mint = t.mint
                }
                Output.emit(
                    mapOf(
                        "amount_sats" to total,
                        "mint_url" to mint,
                        "token_event_id" to lastTokenEventId,
                        "history_event_id" to lastHistoryEventId,
                    ),
                )
                0
            } catch (e: Exception) {
                Output.error("mint_proofs_spent", describe(e))
            }
        }
    }

    private suspend fun nutzapSweep(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val mintFilter = Args(rest).flag("mint")?.trimEnd('/')
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val snap = ctx.cashuSnapshot()
            val wallet = snap.walletEvent ?: return Output.error("no_wallet", "no wallet to redeem into")
            val privkey = runCatching { wallet.privkey(ctx.signer) }.getOrNull() ?: return Output.error("signer_error", "could not decrypt wallet key")
            val p2pkPubkey = snap.nutzapInfoEvent?.p2pkPubkey() ?: return Output.error("no_wallet", "no kind:10019 nutzap pubkey")

            val redeemed = mutableListOf<Map<String, Any?>>()
            val skipped = mutableListOf<Map<String, Any?>>()
            for (nutzap in snap.nutzapEvents) {
                if (mintFilter != null && nutzap.mintUrl()?.trimEnd('/') != mintFilter) continue
                try {
                    val r = ctx.cashuOps().redeemNutzap(nutzap, privkey, p2pkPubkey)
                    redeemed.add(
                        mapOf(
                            "nutzap_id" to nutzap.id,
                            "amount_sats" to r.amount,
                            "token_event_id" to r.tokenEvent.id,
                            "history_event_id" to r.historyEvent.id,
                        ),
                    )
                } catch (e: Exception) {
                    skipped.add(mapOf("nutzap_id" to nutzap.id, "reason" to describe(e)))
                }
            }
            Output.emit(mapOf("redeemed" to redeemed, "skipped" to skipped))
        }
        return 0
    }

    private fun describe(e: Throwable): String = e.message ?: e::class.simpleName ?: "error"
}
