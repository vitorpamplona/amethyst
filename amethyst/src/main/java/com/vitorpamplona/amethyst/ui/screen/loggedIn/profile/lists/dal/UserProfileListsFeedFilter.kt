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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.lists.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent

private val UserProfileDisplayListKinds =
    listOf(
        BookmarkListEvent.KIND,
        OldBookmarkListEvent.KIND,
        PinListEvent.KIND,
        PeopleListEvent.KIND,
        FollowListEvent.KIND,
        HashtagListEvent.KIND,
    )

class UserProfileListsFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + user.pubkeyHex + "-ProfileLists"

    override fun feed(): List<Note> =
        sort(
            LocalCache.addressables.filter(UserProfileDisplayListKinds, user.pubkeyHex) { _, note ->
                acceptableEvent(note)
            },
        )

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptableEvent(it) }

    private fun acceptableEvent(note: Note): Boolean =
        note.event?.pubKey == user.pubkeyHex &&
            isDisplayListEvent(note) &&
            account.isAcceptable(note)

    private fun isDisplayListEvent(note: Note): Boolean =
        when (note.event) {
            is BookmarkListEvent,
            is OldBookmarkListEvent,
            is PinListEvent,
            is PeopleListEvent,
            is FollowListEvent,
            is HashtagListEvent,
            -> true

            else -> false
        }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
