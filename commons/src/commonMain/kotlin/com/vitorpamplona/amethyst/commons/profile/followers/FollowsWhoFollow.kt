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
package com.vitorpamplona.amethyst.commons.profile.followers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The people [account] follows who also follow [target], highest trust rank first: the
 * "Followed by Alice, Bob and 4 others" line that tells a stranger from someone your circle knows.
 *
 * Recounts from the cache whenever one of the account's follows gets a new follow list (one that
 * adds [target] or one that drops it), when the account's own follows change, and when its block
 * list does. It starts no relay request of its own; `UserHoverCardFilterAssembler` asks for these
 * lists while a card is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun followsWhoFollow(
    account: Account,
    target: HexKey,
): Flow<List<User>> {
    val me = account.signer.pubKey

    val followsAndTheirListUpdates =
        account.kind3FollowList.flow
            .map { follows -> follows.authors.filter { it != target && it != me } }
            .distinctUntilChanged()
            .flatMapLatest { follows ->
                if (follows.isEmpty()) {
                    flowOf(follows)
                } else {
                    // Every new list from a follow, not only those that tag the target: a list that
                    // drops the target matters as much, and a filter on the tag would never see it.
                    account.cache
                        .observeNewEvents<Event>(Filter(kinds = listOf(ContactListEvent.KIND), authors = follows))
                        .map { follows }
                        .onStart { emit(follows) }
                }
            }

    return combine(followsAndTheirListUpdates, account.hiddenUsers.flow) { follows, _ -> follows }
        // A card opening pulls in many lists at once; recount once per burst, not once per list.
        .conflate()
        .map { follows ->
            val people =
                followersAmong(
                    follows = follows,
                    target = target,
                    contactListOf = { account.cache.getAddressableNoteIfExists(ContactListEvent.createAddress(it))?.event as? ContactListEvent },
                    isHidden = account::isHidden,
                ).map { account.cache.getOrCreateUser(it) }

            val ranks = people.associateWith { account.trustRankOf(it.pubkeyHex) ?: 0 }
            people.sortedByDescending { ranks[it] }
        }.distinctUntilChanged()
        .flowOn(Dispatchers.Default)
}

/** Which of [follows] list [target] in their newest known follow list, leaving out [isHidden] ones. */
fun followersAmong(
    follows: Collection<HexKey>,
    target: HexKey,
    contactListOf: (HexKey) -> ContactListEvent?,
    isHidden: (HexKey) -> Boolean,
): List<HexKey> =
    follows.filter { key ->
        key != target && !isHidden(key) && contactListOf(key)?.isFollowing(target) == true
    }
