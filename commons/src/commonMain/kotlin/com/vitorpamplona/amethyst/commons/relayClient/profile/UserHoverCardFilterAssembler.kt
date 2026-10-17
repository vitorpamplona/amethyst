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
package com.vitorpamplona.amethyst.commons.relayClient.profile

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/** One open profile hover card: whose it is, and the account's follows when it opened. */
class UserHoverCardQueryState(
    val user: User,
    val follows: Set<HexKey>,
)

/**
 * Loads what a profile hover card shows beyond the metadata the user finder already brings:
 * the person's own follow list (the "following" count) and, of the account's follows, the
 * follow lists that include them ("followed by"). Only those lists, not every follower's: the
 * profile screen's followers tab pulls all of them, far too much for a hover.
 */
class UserHoverCardFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<UserHoverCardQueryState>() {
    val group =
        listOf(
            UserHoverCardFilterSubAssembler(cache, client, ::allKeys),
        )

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun invalidateKeys() = invalidateFilters()

    override fun destroy() = group.forEach { it.destroy() }
}

class UserHoverCardFilterSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<UserHoverCardQueryState>,
) : PerUserEoseManager<UserHoverCardQueryState>(client, allKeys) {
    override fun updateFilter(
        key: UserHoverCardQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = filterUserHoverCard(cache, key.user, key.follows, since)

    override fun user(key: UserHoverCardQueryState) = key.user
}

/** Keeps each REQ's author list well under the message sizes relays accept. */
private const val AUTHORS_PER_FILTER = 300

fun filterUserHoverCard(
    cache: ICacheProvider,
    user: User,
    follows: Set<HexKey>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val hints = cache.relayHints.hintsForKey(user.pubkeyHex)
    val outbox = user.outboxRelays()?.ifEmpty { null } ?: user.allUsedRelaysOrNull() ?: hints
    // Follow lists that tag someone are sent to their inbox, as on the profile's followers tab.
    val inbox = user.inboxRelays()?.ifEmpty { null } ?: user.allUsedRelaysOrNull() ?: hints

    val ownList =
        outbox.map {
            RelayBasedFilter(
                relay = it,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.USER_PROFILE,
                        kinds = listOf(ContactListEvent.KIND),
                        authors = listOf(user.pubkeyHex),
                        limit = 1,
                        since = since?.get(it)?.time,
                    ),
            )
        }

    val authors = follows.filter { it != user.pubkeyHex }.sorted()
    if (authors.isEmpty()) return ownList

    val chunks = authors.chunked(AUTHORS_PER_FILTER)
    val followsWhoFollow =
        inbox.flatMap { relay ->
            chunks.map { chunk ->
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.USER_PROFILE,
                            kinds = listOf(ContactListEvent.KIND),
                            authors = chunk,
                            tags = mapOf("p" to listOf(user.pubkeyHex)),
                            since = since?.get(relay)?.time,
                        ),
                )
            }
        }

    return ownList + followsWhoFollow
}
