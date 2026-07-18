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
    val USAGE: String =
        """
        |Cashu wallet (NIP-60 / NIP-61):
        |  cashu wallet create [--mint URL] [--mints a,b]  publish a kind:17375 wallet + kind:10019
        |        [--privkey HEX] [--relay r1,r2]            nutzap info
        |  cashu wallet show                               P2PK pubkey, mints, balances, counts
        |  cashu wallet export-key                         decrypt + print the wallet's P2PK key
        |  cashu wallet destroy                            withdraw nutzap ad + NIP-09 delete wallet
        |  cashu mint ping URL / info URL                  stateless /v1/info probe (no account)
        |  cashu balance [--mint URL]                      spendable balance from the local store
        |  cashu receive ln SATS [--mint URL]              request a mint quote (bolt11 + kind:7374)
        |  cashu receive complete QUOTE_ID                 poll the quote; mint proofs once settled
        |  cashu receive token TOKEN                       redeem a cashuB… token into the wallet
        |  cashu receive nutzap-sweep [--mint URL]         redeem inbound NIP-61 nutzaps
        |  cashu send ln INVOICE [--mint URL]              melt proofs to pay a bolt11
        |  cashu send token SATS [--mint URL] [--memo S]   export a cashuB… token of SATS
        |  cashu send nutzap USER SATS                     send a P2PK-locked nutzap to USER
        |        [--zapped EVENT_ID] [--message S] [--mint URL]
        |  cashu maintenance scrub [--mint URL]            NUT-07 + NIP-09 prune of spent proofs
        |  cashu maintenance restore MINT_URL              NUT-09 restore proofs from the wallet seed
        |  cashu maintenance migrate-keysets [--mint URL]  consolidate proofs onto the active keyset
        |  cashu mint-rec show [--author NPUB]             NIP-87 mint recommendations (kind:38000)
        |  cashu mint-rec add URL [--dtag X] [--review T]  publish a recommendation
        |  cashu mint-rec remove EVENT_ID                  NIP-09 delete a recommendation
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu",
            tail = tail,
            usage = "cashu <wallet|mint|balance|receive|send|maintenance|mint-rec>",
            help = USAGE,
            routes =
                mapOf(
                    "wallet" to { rest -> CashuWalletCommands.dispatch(dataDir, rest) },
                    "mint" to { rest -> CashuMintCommands.dispatch(rest) },
                    "balance" to { rest -> CashuBalanceCommand.run(dataDir, rest) },
                    "receive" to { rest -> CashuReceiveCommands.dispatch(dataDir, rest) },
                    "send" to { rest -> CashuSendCommands.dispatch(dataDir, rest) },
                    "maintenance" to { rest -> CashuMaintenanceCommands.dispatch(dataDir, rest) },
                    "mint-rec" to { rest -> CashuMintRecCommands.dispatch(dataDir, rest) },
                ),
        )
}
