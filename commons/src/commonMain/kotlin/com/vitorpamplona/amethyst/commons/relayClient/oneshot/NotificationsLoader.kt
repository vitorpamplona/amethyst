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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationItem
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * One page of public notifications for an account over a [OneShotRelayAccess]: the
 * Desktop inbox's subscription filter ([NotificationKinds.subscriptionFilter]), its "is
 * this really for me" rule ([NotificationKinds.tagsAnEventForUser]) and the shared
 * [NotificationItem.classify]. DMs are out: they need the signer to read.
 */
object NotificationsLoader {
    /** Every type [NotificationItem.classify] produces for public kinds. */
    val TYPES = setOf("mention", "reply", "reaction", "repost", "zap")

    /**
     * The newest [limit] notifications for [me] of [types], from the store plus
     * [relays] (normally [me]'s inbox, where others reach them, and outbox). Reactions and reposts count only when
     * their target is [me]'s own note; targets missing from the store are fetched by id
     * from [outboxRelays], where [me]'s notes live.
     */
    suspend fun load(
        access: OneShotRelayAccess,
        me: HexKey,
        relays: Set<NormalizedRelayUrl>,
        outboxRelays: Set<NormalizedRelayUrl>,
        limit: Int,
        since: Long? = null,
        until: Long? = null,
        types: Set<String> = TYPES,
        timeoutMs: Long = 8_000,
    ): List<NotificationItem> {
        // Over-fetch: classification drops what only p-tags us in passing.
        val base = NotificationKinds.subscriptionFilter(me, limit = (limit * 2).coerceAtMost(500), since = since)
        val filter = base.copy(kinds = NotificationKinds.PUBLIC_SUBSCRIPTION_KINDS, until = until)

        val events = (access.query(filter) + access.fetch(relays, filter, timeoutMs).map { it.second }).distinctBy { it.id }
        val authoredByMe = targetsAuthoredBy(access, me, events, outboxRelays, timeoutMs)
        return events
            .asSequence()
            .filter { NotificationKinds.tagsAnEventForUser(it, me) { id -> id in authoredByMe } }
            .mapNotNull(NotificationItem::classify)
            .filter { it.type in types }
            .sortedByDescending { it.timestamp }
            .take(limit)
            .toList()
    }

    /**
     * The ids, among the targets of the reactions and reposts in [events], that [me]
     * authored. A reaction to someone else's note that merely p-tags us is not a
     * notification. Targets missing from the store are fetched in one request.
     */
    private suspend fun targetsAuthoredBy(
        access: OneShotRelayAccess,
        me: HexKey,
        events: List<Event>,
        outboxRelays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): Set<HexKey> {
        val targets = events.mapNotNullTo(mutableSetOf()) { NotificationKinds.interactionTargetId(it) }
        if (targets.isEmpty()) return emptySet()

        val known = access.query(Filter(ids = targets.toList())).associateByTo(mutableMapOf()) { it.id }
        val missing = targets - known.keys
        if (missing.isNotEmpty()) {
            val filter = Filter(ids = missing.toList(), authors = listOf(me))
            access.fetch(outboxRelays, filter, timeoutMs).forEach { (_, event) -> known[event.id] = event }
        }
        return known.values.filter { it.pubKey == me }.mapTo(mutableSetOf()) { it.id }
    }
}
