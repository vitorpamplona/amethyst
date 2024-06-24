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
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class FilterByListParams(
    val isGlobal: Boolean,
    val isHiddenList: Boolean,
    val followLists: Account.LiveFollowLists?,
    val hiddenLists: Account.LiveHiddenUsers,
    val now: Long = TimeUtils.oneMinuteFromNow(),
) {
    fun isNotHidden(userHex: String) = !(hiddenLists.hiddenUsers.contains(userHex) || hiddenLists.spammers.contains(userHex))

    fun isNotInTheFuture(noteEvent: Event) = noteEvent.createdAt <= now

    fun isEventInList(noteEvent: Event): Boolean {
        if (followLists == null) return false

        return if (noteEvent is LiveActivitiesEvent) {
            noteEvent.participantsIntersect(followLists.users) ||
                noteEvent.isTaggedHashes(followLists.hashtags) ||
                noteEvent.isTaggedGeoHashes(followLists.geotags) ||
                noteEvent.isTaggedAddressableNotes(followLists.communities)
        } else {
            noteEvent.pubKey in followLists.users ||
                noteEvent.isTaggedHashes(followLists.hashtags) ||
                noteEvent.isTaggedGeoHashes(followLists.geotags) ||
                noteEvent.isTaggedAddressableNotes(followLists.communities)
        }
    }

    fun isATagInList(aTag: ATag): Boolean {
        if (followLists == null) return false

        return aTag.pubKeyHex in followLists.users
    }

    fun match(
        noteEvent: EventInterface?,
        isGlobalRelay: Boolean = true,
    ) = if (noteEvent is Event) match(noteEvent, isGlobalRelay) else false

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
            followLists: Account.LiveFollowLists?,
            hiddenUsers: Account.LiveHiddenUsers,
        ): FilterByListParams {
            return FilterByListParams(
                isGlobal = selectedListName == GLOBAL_FOLLOWS,
                isHiddenList = showHiddenKey(selectedListName, userHex),
                followLists = followLists,
                hiddenLists = hiddenUsers,
            )
        }
    }
}
