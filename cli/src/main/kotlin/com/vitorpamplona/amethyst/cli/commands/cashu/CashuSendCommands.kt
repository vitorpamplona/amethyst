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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent

/**
 * `amy cashu send <ln|token|nutzap>` — outbound flows on the shared
 * commons CashuWalletOps. All spends scrub the source mint first (NUT-07 +
 * NIP-09), exactly like the Android wallet, so a stale proof can't trip
 * "proofs already spent".
 */
object CashuSendCommands {
    val USAGE: String =
        """
        |Cashu outbound flows (NIP-60 / NIP-61; spends scrub the source mint first):
        |  cashu send ln INVOICE [--mint URL]             melt proofs to pay a bolt11
        |  cashu send token SATS [--mint URL] [--memo S]  export a cashuB… token of SATS
        |  cashu send nutzap USER SATS                    send a P2PK-locked nutzap to USER
        |        [--zapped EVENT_ID] [--message S]         (resolves their kind:10019; --zapped
        |        [--mint URL]                              attributes the zap to an event)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu send",
            tail = tail,
            usage = "cashu send <ln|token|nutzap>",
            help = USAGE,
            routes =
                mapOf(
                    "ln" to { rest -> ln(dataDir, rest) },
                    "token" to { rest -> token(dataDir, rest) },
                    "nutzap" to { rest -> nutzap(dataDir, rest) },
                ),
        )

    private fun pickMint(
        flag: String?,
        mints: List<String>,
    ): String? = flag?.trimEnd('/') ?: mints.firstOrNull()

    private suspend fun ln(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val invoice = args.positional(0, "invoice").trim()
        args.rejectUnknown("mint")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val snap = ctx.cashuSnapshot()
            val mint = pickMint(args.flag("mint"), snap.mints) ?: return Output.error("no_mint", "no mint configured; pass --mint URL")
            return try {
                val quote = ctx.cashuOps().requestMeltQuote(mint, invoice)
                // Scrub stale proofs at this mint before melting (matches Amethyst).
                val scrubbed =
                    ctx
                        .cashuOps()
                        .scrubStaleProofs(mint, snap.tokenEntries.filter { it.content.mint == mint })
                        .map { it.id }
                        .toSet()
                val available = snap.tokenEntries.filter { it.content.mint == mint && it.event.id !in scrubbed }
                if (available.isEmpty()) return Output.error("insufficient_funds", "no proofs available at $mint")
                val done = ctx.cashuOps().meltToLightning(mint, quote, available)
                Output.emit(
                    mapOf(
                        "amount_sats" to done.paidAmount,
                        "fee_paid_sats" to done.fees,
                        "preimage" to done.preimage,
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
        val args = Args(rest)
        val sats = args.positional(0, "sats").toLongOrNull() ?: return Output.error("bad_args", "sats must be a positive integer")
        if (sats <= 0) return Output.error("bad_args", "sats must be positive")
        val memo = args.flag("memo")
        args.rejectUnknown("mint")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val snap = ctx.cashuSnapshot()
            val mint = pickMint(args.flag("mint"), snap.mints) ?: return Output.error("no_mint", "no mint configured; pass --mint URL")
            return try {
                val scrubbed =
                    ctx
                        .cashuOps()
                        .scrubStaleProofs(mint, snap.tokenEntries.filter { it.content.mint == mint })
                        .map { it.id }
                        .toSet()
                val available = snap.tokenEntries.filter { it.content.mint == mint && it.event.id !in scrubbed }
                val balance = available.sumOf { it.content.totalAmount() }
                if (balance < sats) return Output.error("insufficient_funds", "mint $mint has only $balance sat")
                val done = ctx.cashuOps().sendAsToken(mint, sats, available, memo)
                Output.emit(
                    mapOf(
                        "token" to done.cashuToken,
                        "amount_sats" to done.amount,
                        "mint_url" to mint,
                        "history_event_id" to done.historyEvent.id,
                    ),
                )
                0
            } catch (e: Exception) {
                Output.error("mint_unreachable", describe(e))
            }
        }
    }

    private suspend fun nutzap(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val userArg = args.positional(0, "user")
        val sats = args.positional(1, "sats").toLongOrNull() ?: return Output.error("bad_args", "sats must be a positive integer")
        if (sats <= 0) return Output.error("bad_args", "sats must be positive")
        val message = args.flag("message") ?: ""
        val zappedId = args.flag("zapped")
        args.rejectUnknown("mint")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val recipient = ctx.requireUserHex(userArg)
            val snap = ctx.cashuSnapshot()

            // Resolve the recipient's kind:10019 (cache then relay).
            val info = resolveNutzapInfo(ctx, recipient) ?: return Output.error("no_mint", "recipient has no kind:10019 nutzap info")
            val recipientP2pk = info.p2pkPubkey() ?: return Output.error("nutzap_locked_to_wrong_key", "recipient kind:10019 has no P2PK pubkey")
            val recipientMints = info.mints().map { it.mintUrl.trimEnd('/') }.toSet()

            // Pick a mint we both share AND hold a balance at.
            val mint =
                args.flag("mint")?.trimEnd('/')?.takeIf { it in recipientMints }
                    ?: snap.balancesByMint.keys.firstOrNull { it.trimEnd('/') in recipientMints }
                    ?: return Output.error("no_mint", "no shared mint with a balance; recipient mints=$recipientMints")

            val available = snap.tokenEntries.filter { it.content.mint.trimEnd('/') == mint.trimEnd('/') }
            if (available.sumOf { it.content.totalAmount() } < sats) return Output.error("insufficient_funds", "mint $mint has insufficient balance")

            val zapped =
                zappedId?.let { id ->
                    val ev = ctx.store.query<Event>(Filter(ids = listOf(id), limit = 1)).firstOrNull()
                    ev?.let { EventHintBundle(it) }
                }

            return try {
                val sent = ctx.cashuOps().sendNutzap(mint, sats, recipient, recipientP2pk, zapped, message, available)
                Output.emit(
                    mapOf(
                        "nutzap_event_id" to sent.nutzapEvent.id,
                        "recipient_pubkey" to recipient,
                        "mint_url" to mint,
                        "amount_sats" to sent.amount,
                        "history_event_id" to sent.historyEvent.id,
                    ),
                )
                0
            } catch (e: Exception) {
                Output.error("mint_unreachable", describe(e))
            }
        }
    }

    /** Recipient kind:10019 from the local store, falling back to a relay drain. */
    private suspend fun resolveNutzapInfo(
        ctx: Context,
        pubkey: String,
    ): NutzapInfoEvent? {
        val filter = Filter(authors = listOf(pubkey), kinds = listOf(NutzapInfoEvent.KIND), limit = 1)
        (ctx.store.query<Event>(filter).firstOrNull() as? NutzapInfoEvent)?.let { return it }
        ctx.drain(ctx.bootstrapRelays().associateWith { listOf(filter) })
        return ctx.store.query<Event>(filter).firstOrNull() as? NutzapInfoEvent
    }

    private fun describe(e: Throwable): String = e.message ?: e::class.simpleName ?: "error"
}
