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
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
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
 *
 * The subscription stays open while the card does, so a follow list published meanwhile
 * arrives live.
 */
class UserHoverCardFilterAssembler(
    cache: EventCache,
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
    val cache: EventCache,
    client: INostrClient,
    allKeys: () -> Set<UserHoverCardQueryState>,
) : PerUserEoseManager<UserHoverCardQueryState>(client, allKeys) {
    override fun updateFilter(
        key: UserHoverCardQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> =
        filterUserHoverCard(cache, key.user, key.follows, since) {
            cache.getAddressableNoteIfExists(ContactListEvent.createAddress(it))?.createdAt()
        }

    override fun user(key: UserHoverCardQueryState) = key.user
}

/** Keeps each REQ's author list well under the message sizes relays accept. */
private const val AUTHORS_PER_FILTER = 300

fun filterUserHoverCard(
    cache: ICacheProvider,
    user: User,
    follows: Set<HexKey>,
    since: SincePerRelayMap?,
    // created_at of the follow list the cache already holds for a key, null when none.
    cachedListAt: (HexKey) -> Long?,
): List<RelayBasedFilter> {
    val hints = cache.relayHints.hintsForKey(user.pubkeyHex)
    val outbox = user.outboxRelays()?.ifEmpty { null } ?: user.allUsedRelaysOrNull() ?: hints
    // Follow lists that tag someone are sent to their inbox, as on the profile's followers tab.
    val inbox = user.inboxRelays()?.ifEmpty { null } ?: user.allUsedRelaysOrNull() ?: hints

    // A follow list can run to hundreds of KB: never download the copy already in the cache.
    val ownListSince = cachedListAt(user.pubkeyHex)?.let { it + 1 }

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
                        since = newest(since?.get(it)?.time, ownListSince),
                    ),
            )
        }

    val chunks = chunkByCachedList(follows.filter { it != user.pubkeyHex }, cachedListAt, AUTHORS_PER_FILTER)
    if (chunks.isEmpty()) return ownList

    val followsWhoFollow =
        inbox.flatMap { relay ->
            chunks.map { chunk ->
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.USER_PROFILE,
                            kinds = listOf(ContactListEvent.KIND),
                            authors = chunk.authors,
                            tags = mapOf("p" to listOf(user.pubkeyHex)),
                            since = newest(since?.get(relay)?.time, chunk.since),
                        ),
                )
            }
        }

    return ownList + followsWhoFollow
}

/** Some [authors] and the oldest created_at any of them may still need, null for everything. */
class AuthorChunk(
    val authors: List<HexKey>,
    val since: Long?,
)

/**
 * Splits [authors] into chunks of [size], ordered by the age of the follow list the cache holds
 * for each, so each chunk can ask only for lists newer than its oldest member's: once a few cards
 * have loaded the follows' lists, the next card re-downloads none of them. Authors with nothing
 * cached come first and ask for everything.
 */
fun chunkByCachedList(
    authors: List<HexKey>,
    cachedListAt: (HexKey) -> Long?,
    size: Int,
): List<AuthorChunk> {
    if (authors.isEmpty()) return emptyList()

    val ages = authors.associateWith { cachedListAt(it) }
    val sorted = authors.sortedWith(compareBy<HexKey> { ages[it] ?: Long.MIN_VALUE }.thenBy { it })

    return sorted.chunked(size).map { chunk ->
        // The oldest member's own list is already here, so ask from just after it.
        val oldest = ages[chunk.first()]
        AuthorChunk(chunk.sorted(), oldest?.let { it + 1 })
    }
}

private fun newest(
    a: Long?,
    b: Long?,
): Long? =
    if (a == null) {
        b
    } else if (b == null) {
        a
    } else {
        maxOf(a, b)
    }
