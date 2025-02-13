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

import com.vitorpamplona.amethyst.model.AROUND_ME
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.isTaggedAddressableNotes
import com.vitorpamplona.quartz.nip01Core.tags.geohash.isTaggedGeoHashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHashes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class FilterByListParams(
    val isGlobal: Boolean,
    val isHiddenList: Boolean,
    val isAroundMe: Boolean,
    val followLists: Account.LiveFollowList?,
    val hiddenLists: Account.LiveHiddenUsers,
    val now: Long = TimeUtils.oneMinuteFromNow(),
) {
    fun isNotHidden(userHex: String) = !(hiddenLists.hiddenUsers.contains(userHex) || hiddenLists.spammers.contains(userHex))

    fun isNotInTheFuture(noteEvent: Event) = noteEvent.createdAt <= now

    fun isEventInList(noteEvent: Event): Boolean {
        if (followLists == null) return false
        if (isAroundMe && followLists.geotags.isEmpty()) return false

        return if (noteEvent is LiveActivitiesEvent) {
            noteEvent.participantsIntersect(followLists.authors) ||
                noteEvent.isTaggedHashes(followLists.hashtags) ||
                noteEvent.isTaggedGeoHashes(followLists.geotags) ||
                noteEvent.isTaggedAddressableNotes(followLists.addresses)
        } else if (noteEvent is CommentEvent) {
            // ignore follows and checks only the root scope
            noteEvent.isTaggedHashes(followLists.hashtags) ||
                noteEvent.isTaggedGeoHashes(followLists.geotags) ||
                noteEvent.isTaggedAddressableNotes(followLists.addresses)
        } else {
            noteEvent.pubKey in followLists.authors ||
                noteEvent.isTaggedHashes(followLists.hashtags) ||
                noteEvent.isTaggedGeoHashes(followLists.geotags) ||
                noteEvent.isTaggedAddressableNotes(followLists.addresses)
        }
    }

    fun isATagInList(aTag: ATag): Boolean {
        if (followLists == null) return false

        return aTag.pubKeyHex in followLists.authors
    }

    fun match(
        noteEvent: Event,
        isGlobalRelay: Boolean = true,
    ) = ((isGlobal && isGlobalRelay) || isEventInList(noteEvent)) &&
        (isHiddenList || isNotHidden(noteEvent.pubKey)) &&
        isNotInTheFuture(noteEvent)

    fun match(aTag: ATag?) =
        aTag != null &&
            (isGlobal || isATagInList(aTag)) &&
            (isHiddenList || isNotHidden(aTag.pubKeyHex))

    companion object {
        fun showHiddenKey(
            selectedListName: String,
            userHex: String,
        ) = selectedListName == PeopleListEvent.blockListFor(userHex) || selectedListName == MuteListEvent.blockListFor(userHex)

        fun create(
            userHex: String,
            selectedListName: String,
            followLists: Account.LiveFollowList?,
            hiddenUsers: Account.LiveHiddenUsers,
        ): FilterByListParams =
            FilterByListParams(
                isGlobal = selectedListName == GLOBAL_FOLLOWS,
                isHiddenList = showHiddenKey(selectedListName, userHex),
                isAroundMe = selectedListName == AROUND_ME,
                followLists = followLists,
                hiddenLists = hiddenUsers,
            )
    }
}
