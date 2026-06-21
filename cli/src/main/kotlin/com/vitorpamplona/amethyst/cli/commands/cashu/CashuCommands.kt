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

import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.commands.route

/**
 * `amy cashu …` — NIP-60 Cashu wallet + NIP-61 nutzaps, driven entirely
 * through the shared `commons` wallet code (CashuWalletOps + CashuWalletReader)
 * so amy exercises the exact path the Android app runs.
 *
 * See `cli/plans/2026-05-28-cashu-cli.md` for the full command surface and the
 * stable `--json` contract.
 */
object CashuCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu",
            tail = tail,
            usage = "cashu <wallet|mint|balance>",
            routes =
                mapOf(
                    "wallet" to { rest -> CashuWalletCommands.dispatch(dataDir, rest) },
                    "mint" to { rest -> CashuMintCommands.dispatch(rest) },
                    "balance" to { rest -> CashuBalanceCommand.run(dataDir, rest) },
                ),
        )
}
