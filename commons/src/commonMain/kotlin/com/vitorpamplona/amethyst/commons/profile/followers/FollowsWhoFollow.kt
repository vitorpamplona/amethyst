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
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/**
 * The people [account] follows who also follow [target], highest trust rank first: the
 * "Followed by Alice, Bob and 4 others" line that tells a stranger from someone your circle knows.
 *
 * Reads only the contact lists already in the cache and starts no relay request, so the answer
 * grows as those lists arrive. Blocked follows are left out.
 */
fun followsWhoFollow(
    account: Account,
    target: HexKey,
): List<User> {
    val me = account.signer.pubKey
    val result = ArrayList<User>()

    account.followingKeySet().forEach { key ->
        if (key == target || key == me || account.isHidden(key)) return@forEach

        val contactList = account.cache.getAddressableNoteIfExists(ContactListEvent.createAddress(key))?.event as? ContactListEvent
        if (contactList != null && contactList.isFollowing(target)) {
            account.cache.getUserIfExists(key)?.let(result::add)
        }
    }

    if (result.size < 2) return result

    val ranks = result.associateWith { account.trustRankOf(it.pubkeyHex) ?: 0 }
    return result.sortedByDescending { ranks[it] }
}
