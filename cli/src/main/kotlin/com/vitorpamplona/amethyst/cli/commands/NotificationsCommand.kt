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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.moderation.notifications.effectiveAuthorPubKey
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.NotificationsLoader
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.ProfileLoader
import com.vitorpamplona.amethyst.commons.rendering.RenderSupport
import com.vitorpamplona.amethyst.commons.rendering.json.JsonEventFormatter

/**
 * `amy notifications [--type reply,mention,reaction,repost,zap] [--limit N]
 * [--since TS] [--until TS] [--timeout SECS]` — what other people did that
 * involves you: replies, mentions, reactions and reposts of your notes, zaps.
 *
 * Loaded by commons' [NotificationsLoader] (the Desktop inbox's subscription filter,
 * its "is this really for me" rule and the shared classifier) and rendered by the
 * shared renderer. DMs are left to `amy dm list`, which can decrypt them.
 */
object NotificationsCommand {
    private val TYPES = NotificationsLoader.TYPES

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val limit = args.intFlag("limit", 50)
        if (limit <= 0) return Output.error("bad_args", "--limit must be > 0")
        val since = args.flag("since")?.let { it.toLongOrNull() ?: return Output.error("bad_args", "--since expects unix seconds") }
        val until = args.flag("until")?.let { it.toLongOrNull() ?: return Output.error("bad_args", "--until expects unix seconds") }
        val types =
            args
                .flag("type")
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.toSet() ?: TYPES
        (types - TYPES).firstOrNull()?.let { return Output.error("bad_args", "unknown --type '$it' (expected ${TYPES.joinToString(",")})") }
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex
            val access = NoteSupport.access(ctx)
            val relays = ctx.nip65ReadRelays() + ctx.outboxRelays()

            val items = NotificationsLoader.load(access, me, relays, ctx.outboxRelays(), limit, since, until, types, timeoutMs)
            val renderCtx =
                ProfileLoader.renderContext(access, items.map { it.effectiveAuthorPubKey } + items.map { it.event.pubKey }, fetchMissing = true, timeoutMs = timeoutMs)

            Output.emit(
                mapOf(
                    "pubkey" to me,
                    "queried_relays" to relays.map { it.url },
                    "count" to items.size,
                    "notifications" to
                        items.map { item ->
                            mapOf(
                                "type" to item.type,
                                "from" to JsonEventFormatter.author(RenderSupport.authorRef(item.effectiveAuthorPubKey, renderCtx)),
                            ) + NoteSupport.render(item.event, renderCtx, includeBody = false)
                        },
                ),
            )
            return 0
        }
    }
}
