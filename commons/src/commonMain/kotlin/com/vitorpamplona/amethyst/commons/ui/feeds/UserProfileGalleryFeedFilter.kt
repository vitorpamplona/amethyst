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
package com.vitorpamplona.amethyst.commons.ui.feeds

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.RegularVideoEvent
import com.vitorpamplona.quartz.nip71Video.ReplaceableVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent

class UserProfileGalleryFeedFilter(
    val user: User,
    val account: IAccount,
    val cacheProvider: ICacheProvider,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + "ProfileGallery"

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val notes =
            cacheProvider.filterNotesIntoSet { _, it ->
                acceptableEvent(it, params, user)
            }

        val addressableNotes =
            cacheProvider.filterAddressables(
                listOf(VideoVerticalEvent.KIND, VideoVerticalEvent.KIND),
                user.pubkeyHex,
            ) { _, it ->
                acceptableEvent(it, params, user)
            }

        return sort(addressableNotes + notes).toList()
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

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
            (
                it.event?.pubKey == user.pubkeyHex &&
                    (
                        noteEvent is PictureEvent ||
                            noteEvent is RegularVideoEvent ||
                            (noteEvent is ReplaceableVideoEvent && it is AddressableNote) ||
                            (noteEvent is ProfileGalleryEntryEvent && noteEvent.hasUrl() && noteEvent.hasFromEvent())
                    )
            )
        ) &&
            params.match(noteEvent, it.relays) &&
            account.isAcceptable(it)
    }

    fun buildFilterParams(account: IAccount): FilterByListParams =
        FilterByListParams.create(
            followLists = account.getLiveFollowLists("stories"),
            hiddenUsers = account.liveHiddenUsers,
        )

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
