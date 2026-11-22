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
package com.vitorpamplona.amethyst.commons.profile.lists.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent

/**
 * The public people lists (NIP-51 follow sets, kind 30000) and follow packs (kind 39089) the
 * viewed profile published. The profile subscription already loads both kinds, so this only
 * reads the addressable cache. The user's block list, a kind 30000 with a reserved d-tag, is
 * not a list to browse and stays out. So do lists without a single public member: other clients
 * have stored settings as kind 30000 (e.g. `notifications/lastOpened`), and a list whose members
 * are all private shows up empty to everyone else anyway.
 *
 * Unlike the feeds, this ignores the top-bar follow-list choice: the lists belong to the profile
 * being viewed, whether or not the account follows its owner.
 */
class UserProfileListsFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + "ProfileLists" + "-" + user.pubkeyHex

    override fun feed(): List<Note> {
        val lists =
            LocalCache.addressables
                .filter(LIST_KINDS, user.pubkeyHex) { _, it ->
                    acceptableEvent(it)
                }

        return sort(lists.toSet()).toList()
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptableEvent(it) }

    private fun acceptableEvent(it: Note): Boolean {
        val noteEvent = it.event ?: return false
        if (noteEvent.pubKey != user.pubkeyHex) return false

        val isBrowsableList =
            when (noteEvent) {
                is StarterPackEvent -> noteEvent.followIdSet().isNotEmpty()
                is FollowSetEvent -> noteEvent.dTag() != FollowSetEvent.BLOCK_LIST_D_TAG && noteEvent.publicUsersIdSet().isNotEmpty()
                else -> false
            }

        return isBrowsableList && account.isAcceptable(it)
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()

    companion object {
        val LIST_KINDS = listOf(FollowSetEvent.KIND, StarterPackEvent.KIND)
    }
}
