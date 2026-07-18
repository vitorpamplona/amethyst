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
 * `amy cashu balance [--mint URL]` — spendable balance from the local store,
 * via the shared CashuWalletReader projection. Optionally filtered to one mint.
 */
object CashuBalanceCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val mintFilter = args.flag("mint")?.trimEnd('/')
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            val snap = ctx.cashuSnapshot()
            val byMint =
                snap.balancesByMint.let { all ->
                    if (mintFilter == null) all else all.filterKeys { it.trimEnd('/') == mintFilter }
                }
            Output.emit(
                mapOf(
                    "balance_sats" to byMint.values.sum(),
                    "balances_by_mint" to byMint,
                    "proofs_count" to
                        snap.tokenEntries
                            .filter { mintFilter == null || it.content.mint.trimEnd('/') == mintFilter }
                            .sumOf { it.content.proofs.size },
                ),
            )
        }
        return 0
    }
}
