/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.ProfileGalleryEntryEvent

class UserProfileGalleryFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + "ProfileGallery"

    override fun showHiddenKey(): Boolean =
        account.defaultStoriesFollowList.value == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultStoriesFollowList.value == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, params, user)
            }

        var sorted = sort(notes)
        return sorted.toList()
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, params, user) }
    }

    fun acceptableEvent(
        it: Note,
        params: FilterByListParams,
        user: User,
    ): Boolean {
        val noteEvent = it.event
        return (
            (it.event?.pubKey() == user.pubkeyHex && noteEvent is ProfileGalleryEntryEvent) && noteEvent.hasUrl() && noteEvent.hasFromEvent() // && noteEvent.isOneOf(SUPPORTED_VIDEO_FEED_MIME_TYPES_SET))
        ) &&
            params.match(noteEvent) &&
            account.isAcceptable(it)
    }

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.defaultStoriesFollowList.value,
            followLists = account.liveStoriesFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)
}
