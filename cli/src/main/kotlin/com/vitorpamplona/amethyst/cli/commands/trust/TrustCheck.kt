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
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.DefaultMinTrustScore
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdict
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/**
 * `amy trust check USER… [--observer USER] [--min-score N]`
 *
 * Known or stranger, and why, for each USER (npub, hex, alias or NIP-05): the verdict the DM
 * tabs, Curated notifications and collapsed replies use (`TrustVerdicts.explain`), plus the
 * provider's rank, follower count and hops. Reads the local index and contact list; run
 * `amy trust sync` first. With `--observer`, answers for that user's network (their contact
 * list is fetched when the local store has none).
 */
object TrustCheck {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val minScore = args.intFlag("min-score", DefaultMinTrustScore)
        val observerArg = args.flag("observer")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        val users = args.positional
        if (users.isEmpty()) return Output.error("bad_args", "usage: amy trust check USER… [--observer USER] [--min-score N]")

        Context.open(dataDir).use { ctx ->
            val me = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            var contacts = ctx.contactsOf(me)
            if (contacts == null && me != ctx.identity.pubKeyHex) {
                ctx.drain(
                    (ctx.bootstrapRelays() + Constants.eventFinderRelays).associateWith { listOf(Filter(kinds = listOf(ContactListEvent.KIND), authors = listOf(me), limit = 1)) },
                    timeoutMs,
                )
                contacts = ctx.contactsOf(me)
            }
            if (contacts == null) System.err.println("[amy] no contact list for $me in the local store: follows are not counted")
            val follows = contacts?.verifiedFollowKeySet().orEmpty()

            openTrustNetwork(ctx, me, minScore).use { session ->
                val index =
                    session.state.network.value
                        ?.index
                val results =
                    users.map { input ->
                        val hex = ctx.requireUserHex(input)
                        val verdict = session.state.explain(hex, me, follows)
                        mapOf(
                            "user" to input,
                            "pubkey" to hex,
                            "verdict" to verdict.label(),
                            "reason" to verdict.name.lowercase(),
                            "rank" to index?.rankOf(hex),
                            "followers" to index?.followersOf(hex),
                            "hops" to index?.hopsOf(hex),
                            "follows" to (hex in follows),
                        )
                    }
                Output.emit(
                    mapOf(
                        "observer" to me,
                        "active" to (index != null),
                        "provider" to session.provider?.pubkey,
                        "min_score" to minScore,
                        "results" to results,
                    ),
                )
                return 0
            }
        }
    }

    private fun TrustVerdict.label() =
        when (isKnown) {
            true -> "known"
            false -> "stranger"
            null -> "no_network"
        }
}
