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
package com.vitorpamplona.amethyst.cli.commands.trust

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.model.DefaultMinTrustScore
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkState
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkSyncStatus
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy trust status [--observer USER] [--min-score N]`
 *
 * What the local Web of Trust index holds, with no network: the provider the account's
 * kind 10040 names (from the local store), whether the index on disk belongs to it, how many
 * people it scores and how many pass the minimum score, when it last synced, whether an
 * update or full check is due, file sizes and how long the index took to load.
 */
object TrustStatus {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val minScore = args.intFlag("min-score", DefaultMinTrustScore)
        val observerArg = args.flag("observer")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val started = System.nanoTime()
            openTrustNetwork(ctx, observer, minScore).use { session ->
                val loadMs = (System.nanoTime() - started) / 1_000_000
                val network = session.state.network.value
                val header = network?.header
                val due = TrustNetworkState.dueSync(network, TimeUtils.now(), force = false)
                Output.emit(
                    mapOf(
                        "observer" to observer,
                        "provider" to session.provider?.pubkey,
                        "relay" to session.provider.relayOrNull(),
                        "active" to (network != null),
                        "entries" to (network?.index?.size ?: 0),
                        "passing" to (network?.index?.countAtLeast(minScore) ?: 0),
                        "min_score" to minScore,
                        "sync_cursor" to header?.syncCursor,
                        "last_update" to header?.lastUpdate,
                        "last_full_check" to header?.lastFullCheck,
                        "update_due" to (due != null),
                        "full_check_due" to (due == TrustNetworkSyncStatus.Kind.FULL_CHECK || due == TrustNetworkSyncStatus.Kind.DOWNLOAD),
                        "index_bytes" to session.indexFile().takeIf { it.exists() }?.length(),
                        "ids_bytes" to session.idsFile().takeIf { it.exists() }?.length(),
                        "load_ms" to loadMs,
                    ),
                )
                return 0
            }
        }
    }
}
