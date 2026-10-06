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
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationItem
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationKinds
import com.vitorpamplona.amethyst.commons.moderation.notifications.effectiveAuthorPubKey
import com.vitorpamplona.amethyst.commons.rendering.RenderSupport
import com.vitorpamplona.amethyst.commons.rendering.json.JsonEventFormatter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * `amy notifications [--type reply,mention,reaction,repost,zap] [--limit N]
 * [--since TS] [--until TS] [--timeout SECS]` — what other people did that
 * involves you: replies, mentions, reactions and reposts of your notes, zaps.
 *
 * The subscription filter and the "is this really for me" rule are the shared
 * [NotificationKinds] the Desktop inbox uses; each row is typed by the shared
 * [NotificationItem.classify] and rendered by the shared renderer. DMs are left to
 * `amy dm list`, which can decrypt them.
 */
object NotificationsCommand {
    private val TYPES = setOf("mention", "reply", "reaction", "repost", "zap")

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

            val base = NotificationKinds.subscriptionFilter(me, limit = (limit * 2).coerceAtMost(500), since = since)
            val filter = base.copy(kinds = NotificationKinds.PUBLIC_SUBSCRIPTION_KINDS, until = until)
            val relays = ctx.nip65ReadRelays() + ctx.outboxRelays()

            val events =
                (ctx.store.query<Event>(filter) + ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs).map { it.second })
                    .distinctBy { it.id }

            val authoredByMe = targetsAuthoredBy(ctx, me, events, timeoutMs)
            val items =
                events
                    .asSequence()
                    .filter { NotificationKinds.tagsAnEventForUser(it, me) { id -> id in authoredByMe } }
                    .mapNotNull(NotificationItem::classify)
                    .filter { it.type in types }
                    .sortedByDescending { it.timestamp }
                    .take(limit)
                    .toList()

            val renderCtx =
                NoteSupport.renderContext(ctx, items.map { it.effectiveAuthorPubKey } + items.map { it.event.pubKey }, fetchMissing = true, timeoutMs = timeoutMs)

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

    /**
     * The ids, among the targets of the reactions and reposts in [events], that
     * [me] authored. A reaction to someone else's note that merely p-tags us is not a
     * notification. Targets missing from the store are fetched by id from our own
     * outbox (where our notes live) in a single drain.
     */
    private suspend fun targetsAuthoredBy(
        ctx: Context,
        me: HexKey,
        events: List<Event>,
        timeoutMs: Long,
    ): Set<HexKey> {
        val targets = events.mapNotNullTo(mutableSetOf()) { NotificationKinds.interactionTargetId(it) }
        if (targets.isEmpty()) return emptySet()

        val known =
            ctx.store
                .query<Event>(Filter(ids = targets.toList()))
                .associateBy { it.id }
                .toMutableMap()
        val missing = targets - known.keys
        if (missing.isNotEmpty()) {
            val filter = Filter(ids = missing.toList(), authors = listOf(me))
            ctx.drain(ctx.outboxRelays().associateWith { listOf(filter) }, timeoutMs).forEach { (_, ev) -> known[ev.id] = ev }
        }
        return known.values.filter { it.pubKey == me }.mapTo(mutableSetOf()) { it.id }
    }
}
