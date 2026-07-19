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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent

/**
 * `amy cashu mint-rec <show|add|remove>` — NIP-87 mint recommendations
 * (kind:38000), on the shared commons CashuWalletOps.
 */
object CashuMintRecCommands {
    val USAGE: String =
        """
        |NIP-87 mint recommendations (kind:38000):
        |  cashu mint-rec show [--author NPUB]             list recommendations (own by default;
        |                                                   --author drains relays for another user)
        |  cashu mint-rec add URL [--dtag X] [--review T]  publish a recommendation for a mint
        |  cashu mint-rec remove EVENT_ID                  NIP-09 delete a recommendation
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            name = "cashu mint-rec",
            tail = tail,
            usage = "cashu mint-rec <show|add|remove>",
            help = USAGE,
            routes =
                mapOf(
                    "show" to { rest -> show(dataDir, rest) },
                    "add" to { rest -> add(dataDir, rest) },
                    "remove" to { rest -> remove(dataDir, rest) },
                ),
        )

    private fun render(e: MintRecommendationEvent) =
        mapOf(
            "event_id" to e.id,
            "mint_url" to e.mintUrls().firstOrNull(),
            "dtag" to e.dTag(),
            "review" to e.content,
            "pubkey_hex" to e.pubKey,
            "created_at" to e.createdAt,
        )

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.flag("author")
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val recs =
                if (author == null) {
                    ctx.cashuSnapshot().recommendations
                } else {
                    val pk = ctx.requireUserHex(author)
                    val filter = Filter(authors = listOf(pk), kinds = listOf(MintRecommendationEvent.KIND))
                    if (ctx.store.query<Event>(filter).isEmpty()) ctx.drain(ctx.bootstrapRelays().associateWith { listOf(filter) })
                    ctx.store
                        .query<Event>(filter)
                        .filterIsInstance<MintRecommendationEvent>()
                        .sortedByDescending { it.createdAt }
                }
            Output.emit(mapOf("recommendations" to recs.map { render(it) }))
        }
        return 0
    }

    private suspend fun add(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val url = args.positional(0, "mint-url").trimEnd('/')
        args.rejectUnknown("dtag", "review")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val event = ctx.cashuOps().recommendMint(mintUrl = url, mintAnnouncementDTag = args.flag("dtag"), review = args.flag("review") ?: "")
            Output.emit(mapOf("event_id" to event.id, "mint_url" to url))
        }
        return 0
    }

    private suspend fun remove(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val id = args.positional(0, "event-id")
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val event =
                ctx.cashuSnapshot().recommendations.firstOrNull { it.id == id }
                    ?: (ctx.store.query<Event>(Filter(ids = listOf(id), limit = 1)).firstOrNull() as? MintRecommendationEvent)
                    ?: return Output.error("bad_args", "no recommendation event $id")
            ctx.cashuOps().deleteRecommendation(event)
            Output.emit(mapOf("deletion_event_id" to id, "deleted" to true))
        }
        return 0
    }
}
