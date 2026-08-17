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

/**
 * `amy cashu sync` — page the whole NIP-60/61 event set off the relays into the
 * local store, then report the resulting balance.
 *
 * Every other `cashu` read command projects the local store and never touches
 * the network, which is what makes them instant and offline-capable — but it
 * also means they only ever saw whatever else had filled the store, and nothing
 * in amy fetched the NIP-60 kinds at all. This is the verb that fills it.
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
            val snap = ctx.cashuSnapshot()
            Output.emit(
                mapOf(
                    "events_downloaded" to downloaded,
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
}
