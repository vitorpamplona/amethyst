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

/**
 * `amy cashu maintenance <scrub|restore|migrate-keysets>` — wallet hygiene,
 * all on the shared commons CashuWalletOps.
 */
object CashuMaintenanceCommands {
    val USAGE: String =
        """
        |Cashu wallet hygiene:
        |  cashu maintenance scrub [--mint URL]             NUT-07 + NIP-09 prune of spent proofs
        |  cashu maintenance restore MINT_URL               NUT-09 restore unspent proofs from the
        |                                                    wallet seed
        |  cashu maintenance migrate-keysets [--mint URL]   consolidate proofs onto each mint's
        |                                                    active keyset
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu maintenance",
            tail = tail,
            usage = "cashu maintenance <scrub|restore|migrate-keysets>",
            help = USAGE,
            routes =
                mapOf(
                    "scrub" to { rest -> scrub(dataDir, rest) },
                    "restore" to { rest -> restore(dataDir, rest) },
                    "migrate-keysets" to { rest -> migrate(dataDir, rest) },
                ),
        )

    private suspend fun scrub(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val mintFilter = args.flag("mint")?.trimEnd('/')
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val byMint =
                ctx
                    .cashuSnapshot()
                    .tokenEntries
                    .groupBy { it.content.mint }
                    .filterKeys { mintFilter == null || it.trimEnd('/') == mintFilter }
            val scrubbed = mutableListOf<Map<String, Any?>>()
            var keptCount = 0
            for ((mint, entries) in byMint) {
                val staleEvents = runCatching { ctx.cashuOps().scrubStaleProofs(mint, entries) }.getOrDefault(emptyList())
                val staleIds = staleEvents.map { it.id }.toSet()
                entries.filter { it.event.id in staleIds }.forEach {
                    scrubbed.add(mapOf("event_id" to it.event.id, "amount_sats" to it.content.totalAmount(), "mint_url" to mint))
                }
                keptCount += entries.count { it.event.id !in staleIds }
            }
            Output.emit(mapOf("scrubbed" to scrubbed, "kept_count" to keptCount))
        }
        return 0
    }

    private suspend fun restore(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val mint = args.positional(0, "mint-url").trimEnd('/')
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            return try {
                val outcome = ctx.cashuRestore(mint) ?: return Output.error("no_wallet", "no wallet seed to restore from")
                Output.emit(
                    mapOf(
                        "mint_url" to mint,
                        "sats_recovered" to outcome.amountRecoveredSats,
                        "proofs_recovered" to outcome.proofsRecovered,
                        "token_event_id" to outcome.tokenEvent?.id,
                    ),
                )
                0
            } catch (e: Exception) {
                Output.error("mint_unreachable", e.message ?: "restore failed")
            }
        }
    }

    private suspend fun migrate(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val mintFilter = args.flag("mint")?.trimEnd('/')
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val byMint =
                ctx
                    .cashuSnapshot()
                    .tokenEntries
                    .groupBy { it.content.mint }
                    .filterKeys { mintFilter == null || it.trimEnd('/') == mintFilter }
            val migrated = mutableListOf<Map<String, Any?>>()
            for ((mint, entries) in byMint) {
                val activeId = runCatching { ctx.cashuOps().fetchActiveKeysetId(mint) }.getOrNull() ?: continue
                val stale = entries.filter { entry -> entry.content.proofs.any { it.id != activeId } }
                if (stale.isEmpty()) continue
                val result = runCatching { ctx.cashuOps().migrateToActiveKeyset(mint, stale, activeId) }.getOrNull() ?: continue
                migrated.add(
                    mapOf(
                        "mint_url" to mint,
                        "old_event_ids" to stale.map { it.event.id },
                        "amount_sats" to result.amountMigrated,
                        "proofs_migrated" to result.proofsMigrated,
                    ),
                )
            }
            Output.emit(mapOf("migrated" to migrated))
        }
        return 0
    }
}
