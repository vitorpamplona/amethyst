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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The people [account] follows who also follow [target], highest trust rank first: the
 * "Followed by Alice, Bob and 4 others" line that tells a stranger from someone your circle knows.
 *
 * Emits what the cache holds now and again as more of those follow lists arrive. It starts no
 * relay request of its own; `UserHoverCardFilterAssembler` asks for exactly these lists.
 * Blocked follows are left out.
 */
fun followsWhoFollow(
    account: Account,
    target: HexKey,
): Flow<List<User>> {
    val me = account.signer.pubKey
    val follows = account.followingKeySet().filter { it != target && it != me }
    if (follows.isEmpty()) return flowOf(emptyList())

    val filter =
        Filter(
            kinds = listOf(ContactListEvent.KIND),
            authors = follows,
            tags = mapOf("p" to listOf(target)),
        )

    return account.cache
        .observeEvents<ContactListEvent>(filter)
        .map { lists ->
            val people =
                lists.mapNotNullTo(LinkedHashSet()) { list ->
                    if (account.isHidden(list.pubKey)) null else account.cache.getOrCreateUser(list.pubKey)
                }
            val ranks = people.associateWith { account.trustRankOf(it.pubkeyHex) ?: 0 }
            people.sortedByDescending { ranks[it] }
        }.flowOn(Dispatchers.Default)
}
