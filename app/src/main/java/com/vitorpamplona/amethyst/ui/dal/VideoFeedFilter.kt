/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class VideoFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultStoriesFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultStoriesFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultStoriesFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        val notes = innerApplyFilter(LocalCache.notes.values)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = TimeUtils.now()
        val isGlobal = account.defaultStoriesFollowList.value == GLOBAL_FOLLOWS
        val isHiddenList =
            account.defaultStoriesFollowList.value ==
                PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
                account.defaultStoriesFollowList.value ==
                MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

        val followingKeySet = account.liveStoriesFollowLists.value?.users ?: emptySet()
        val followingTagSet = account.liveStoriesFollowLists.value?.hashtags ?: emptySet()
        val followingGeohashSet = account.liveStoriesFollowLists.value?.geotags ?: emptySet()

        return collection
            .asSequence()
            .filter {
                (it.event is FileHeaderEvent && (it.event as FileHeaderEvent).hasUrl()) ||
                    it.event is FileStorageHeaderEvent
            }
            .filter {
                isGlobal ||
                    it.author?.pubkeyHex in followingKeySet ||
                    (it.event?.isTaggedHashes(followingTagSet) ?: false) ||
                    (it.event?.isTaggedGeoHashes(followingGeohashSet) ?: false)
            }
            .filter { isHiddenList || account.isAcceptable(it) }
            .filter { it.createdAt()!! <= now }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
