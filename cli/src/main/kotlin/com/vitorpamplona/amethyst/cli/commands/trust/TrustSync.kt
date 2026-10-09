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
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkOutcome
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkSyncStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * `amy trust sync [--update|--full|--redownload] [--observer USER] [--min-score N] [--timeout SECS]`
 *
 * Brings `~/.amy/<account>/wot/` up to date with the account's `30382:rank` provider, exactly
 * as the apps do: a first download when there is no index, otherwise an update (or the weekly
 * negentropy full check when it is due). The flags force one kind. Every signature is checked.
 * Progress goes to stderr; the result (with timings) to stdout. `--observer` syncs another
 * user's network instead, read-only, from the public entries of their kind 10040.
 */
object TrustSync {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val update = args.bool("update")
        val full = args.bool("full")
        val redownload = args.bool("redownload")
        val minScore = args.minScore()
        val observerArg = args.flag("observer")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        if (listOf(update, full, redownload).count { it } > 1) {
            return Output.error("bad_args", "pick at most one of --update, --full, --redownload")
        }
        val kind =
            when {
                redownload -> TrustNetworkSyncStatus.Kind.DOWNLOAD
                full -> TrustNetworkSyncStatus.Kind.FULL_CHECK
                update -> TrustNetworkSyncStatus.Kind.UPDATE
                else -> null
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            openTrustNetwork(ctx, observer, minScore, refresh = true, withClient = true, timeoutMs = timeoutMs).use { session ->
                val provider =
                    session.provider
                        ?: return if (session.undecryptable) {
                            // Not "no provider": setting one up would replace the one it may hold.
                            Output.error("undecryptable", "could not decrypt the private part of the account's kind 10040, which may name its provider; check the signer")
                        } else {
                            noProvider(observer.takeIf { it != ctx.identity.pubKeyHex })
                        }
                System.err.println("[amy] trust network: provider ${provider.pubkey} @ ${provider.relayUrl.url}")

                val started = System.currentTimeMillis()
                val run =
                    coroutineScope {
                        val progress =
                            launch {
                                session.state.status
                                    .distinctUntilChangedBy { it.verified / 25_000 }
                                    .collect { s -> s.running?.let { System.err.println("[amy] ${it.name.lowercase()}: ${s.verified} verified${s.expected?.let { e -> " of $e" } ?: ""}") } }
                            }
                        try {
                            session.state.syncNow(kind)
                        } finally {
                            progress.cancel()
                        }
                    } ?: return Output.error("sync_not_started", "no sync started: another one is running, or the index on disk is not matched to this provider yet")
                val elapsed = System.currentTimeMillis() - started

                val index =
                    session.state.network.value
                        ?.index
                val result = run.result
                val fields =
                    mapOf(
                        "observer" to observer,
                        "kind" to run.kind.name.lowercase(),
                        "provider" to provider.pubkey,
                        "relay" to provider.relayUrl.url,
                        "applied" to run.applied,
                        "complete" to (result?.complete ?: run.applied),
                        "received" to (result?.received ?: 0),
                        "invalid" to (result?.invalid ?: 0),
                        "detail" to run.outcome.describe(),
                        "entries" to (index?.size ?: 0),
                        "passing" to (index?.countAtLeast(minScore) ?: 0),
                        "min_score" to minScore,
                        "elapsed_ms" to elapsed,
                    )
                if (!run.applied) return Output.error("sync_failed", run.outcome.describe(), fields)
                Output.emit(fields)
                return 0
            }
        }
    }
}

/** One line for humans and the `detail` field. */
private fun TrustNetworkOutcome.describe(): String? =
    when (this) {
        is TrustNetworkOutcome.Applied -> result.detail
        is TrustNetworkOutcome.Unchanged -> "nothing new"
        TrustNetworkOutcome.NoScoresYet -> "the provider has published no scores yet"
        TrustNetworkOutcome.WaitingForUnmetered -> "waiting for an unmetered network"
        TrustNetworkOutcome.ProviderChanged -> "the provider changed during the sync"
        is TrustNetworkOutcome.Incomplete -> detail ?: "incomplete"
        is TrustNetworkOutcome.Failed -> message
    }

/** No `30382:rank` provider in the kind 10040 of [observer] (null: the account's own). */
internal fun noProvider(observer: String? = null): Int =
    Output.error(
        "no_provider",
        if (observer == null) {
            "the account's kind 10040 names no 30382:rank provider; run `amy trust setup brainstorm`, `amy trust copy USER` or `amy graperank register PROVIDER --relay URL`"
        } else {
            "$observer's kind 10040 names no public 30382:rank provider"
        },
    )
