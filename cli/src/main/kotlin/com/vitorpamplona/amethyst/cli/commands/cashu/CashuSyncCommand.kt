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
import com.vitorpamplona.amethyst.commons.cashu.ops.CounterSettlement

/**
 * `amy cashu sync` — page the whole NIP-60/61 event set off the relays into the
 * local store, align this install's NUT-13 counters with the mints, then report
 * the resulting balance.
 *
 * Every other `cashu` read command projects the local store and never touches
 * the network, which is what makes them instant and offline-capable — but it
 * also means they only ever saw whatever else had filled the store, and nothing
 * in amy fetched the NIP-60 kinds at all. This is the verb that fills it.
 *
 * It ends with what the app does after its own startup backfill
 * ([com.vitorpamplona.amethyst.cli.CashuContext.settleCounters]): once per
 * keyset, a NUT-09 scan moves this data dir's NUT-13 counter past every slot
 * the mint signed for the wallet's seed, so the first spend from a data dir
 * that was set up again, or shares the wallet with a phone, isn't refused for
 * re-deriving outputs the mint signed already.
 *
 * Reports both the balance and the proof/history counts so a caller can tell a
 * genuinely empty wallet from an unsynced one.
 */
object CashuSyncCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            val downloaded = ctx.cashu.sync()
            val settled = ctx.cashu.settleCounters()
            val snap = ctx.cashuSnapshot()
            Output.emit(
                mapOf(
                    "events_downloaded" to downloaded,
                    "counters_settled" to settled.mapValues { (_, it) -> it.describe() },
                    "balance_sats" to snap.balanceSats,
                    "balances_by_mint" to snap.balancesByMint,
                    "proofs_count" to snap.tokenEntries.sumOf { it.content.proofs.size },
                    "token_events" to snap.tokenEntries.size,
                    "history_events" to snap.history.size,
                ),
            )
        }
        return 0
    }

    /** One settlement as plain values, for the JSON contract. */
    private fun CounterSettlement.describe(): Map<String, Any> =
        when (this) {
            is CounterSettlement.AlreadySettled -> mapOf("keyset_id" to keysetId, "status" to "already_settled")
            is CounterSettlement.Unsupported -> mapOf("keyset_id" to keysetId, "status" to "mint_has_no_restore")
            is CounterSettlement.Trusted -> mapOf("keyset_id" to keysetId, "status" to "counter_too_far_to_scan")
            is CounterSettlement.Settled ->
                mapOf(
                    "keyset_id" to keysetId,
                    "status" to "settled",
                    "slots_skipped" to slotsSkipped,
                    "proofs_recovered" to proofsRecovered,
                    "sats_recovered" to amountRecoveredSats,
                )
        }
}
