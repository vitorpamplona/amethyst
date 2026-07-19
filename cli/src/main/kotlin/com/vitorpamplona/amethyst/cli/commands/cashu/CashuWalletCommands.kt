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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * `amy cashu wallet <create|show|export-key|destroy>`.
 *
 *   create [--mint URL] [--mints a,b] [--privkey HEX] [--relay r1,r2]
 *   show
 *   export-key
 *   destroy
 */
object CashuWalletCommands {
    val USAGE: String =
        """
        |Cashu wallet lifecycle (NIP-60):
        |  cashu wallet create [--mint URL] [--mints a,b]  publish a kind:17375 wallet + kind:10019
        |        [--privkey HEX] [--relay r1,r2]            nutzap info (advertises your inbox relays
        |                                                   for nutzaps unless --relay overrides)
        |  cashu wallet show                               P2PK pubkey, mints, balance, per-mint
        |                                                   balances, proof/history/pending counts
        |  cashu wallet export-key                         decrypt + print the wallet's P2PK key
        |  cashu wallet destroy                            withdraw the nutzap advertisement and
        |                                                   NIP-09 delete the wallet (token events
        |                                                   stay — the ecash still lives at the mint)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu wallet",
            tail = tail,
            usage = "cashu wallet <create|show|export-key|destroy>",
            help = USAGE,
            routes =
                mapOf(
                    "create" to { rest -> create(dataDir, rest) },
                    "show" to { rest -> show(dataDir, rest) },
                    "export-key" to { rest -> exportKey(dataDir, rest) },
                    "destroy" to { rest -> destroy(dataDir, rest) },
                ),
        )

    private fun Args.csv(key: String): List<String> =
        flag(key)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private suspend fun create(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val mints = (args.csv("mint") + args.csv("mints") + args.positional.toList()).distinct()
        if (mints.isEmpty()) return Output.error("bad_args", "at least one --mint URL is required")
        val privkey = args.flag("privkey")
        val explicitRelays = args.csv("relay").mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // Match Amethyst: kind:10019 advertises the account's NIP-65
            // inbox (read) relays as nutzap-receiving relays unless the caller
            // overrides with --relay, so senders publish kind:9321 where we
            // read incoming events.
            val nutzapRelays = explicitRelays.ifEmpty { ctx.nip65ReadRelays().toList() }
            val created =
                try {
                    ctx.cashuOps().publishWalletEvents(mints, privkey, nutzapRelays)
                } catch (e: IllegalArgumentException) {
                    return Output.error("bad_args", e.message)
                }
            Output.emit(
                mapOf(
                    "wallet_event_id" to created.walletEvent.id,
                    "nutzap_info_event_id" to created.nutzapInfo.id,
                    "p2pk_pubkey" to created.p2pkPubkeyHex,
                    "mints" to mints,
                ),
            )
        }
        return 0
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Context.open(dataDir).use { ctx ->
            val snap = ctx.cashuSnapshot()
            if (snap.walletEvent == null) return Output.error("no_wallet", "no kind:17375 wallet in the local store — run `cashu wallet create`")
            Output.emit(
                mapOf(
                    "p2pk_pubkey" to snap.nutzapInfoEvent?.p2pkPubkey(),
                    "mints" to snap.mints,
                    "balance_sats" to snap.balanceSats,
                    "balances_by_mint" to snap.balancesByMint,
                    "proofs_count" to snap.tokenEntries.sumOf { it.content.proofs.size },
                    "history_count" to snap.history.size,
                    "pending_quotes" to snap.pendingQuotes.size,
                ),
            )
        }
        return 0
    }

    private suspend fun exportKey(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Context.open(dataDir).use { ctx ->
            val wallet = ctx.cashuSnapshot().walletEvent ?: return Output.error("no_wallet", "no wallet to export a key from")
            val priv =
                runCatching { wallet.privkey(ctx.signer) }.getOrNull()
                    ?: return Output.error("signer_error", "could not decrypt the wallet P2PK key")
            Output.emit(mapOf("privkey_hex" to priv))
        }
        return 0
    }

    private suspend fun destroy(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val wallet = ctx.cashuSnapshot().walletEvent ?: return Output.error("no_wallet", "no wallet to destroy")
            // Withdraws the nutzap advertisement and NIP-09 deletes the
            // kind:17375; leaves token events (the ecash still lives at the mint).
            ctx.cashuOps().deleteWallet(wallet)
            Output.emit(mapOf("destroyed_wallet_event_id" to wallet.id))
        }
        return 0
    }
}
