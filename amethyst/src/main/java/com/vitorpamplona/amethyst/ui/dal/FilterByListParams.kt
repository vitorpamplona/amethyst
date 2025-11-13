/**
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class FilterByListParams(
    val isHiddenList: Boolean,
    val followLists: IFeedTopNavFilter?,
    val hiddenLists: HiddenUsersState.LiveHiddenUsers,
    val now: Long = TimeUtils.oneMinuteFromNow(),
) {
    fun isNotHidden(userHex: String) = !(hiddenLists.hiddenUsers.contains(userHex) || hiddenLists.spammers.contains(userHex))

    fun isNotInTheFuture(noteEvent: Event) = noteEvent.createdAt <= now

    fun isEventInList(noteEvent: Event): Boolean {
        if (followLists == null) return false

        return followLists.match(noteEvent)
    }

    fun isAuthorInFollows(author: HexKey): Boolean {
        if (followLists == null) return false

        return followLists.matchAuthor(author)
    }

    fun isAuthorInFollows(address: Address): Boolean {
        if (followLists == null) return false

        return followLists.matchAuthor(address.pubKeyHex)
    }

    fun isGlobal(comingFrom: List<NormalizedRelayUrl>) =
        followLists is GlobalTopNavFilter &&
            comingFrom.any { followLists.outboxRelays.value.contains(it) }

    fun match(
        noteEvent: Event,
        comingFrom: List<NormalizedRelayUrl>,
    ) = ((isGlobal(comingFrom)) || isEventInList(noteEvent)) &&
        (isHiddenList || isNotHidden(noteEvent.pubKey)) &&
        isNotInTheFuture(noteEvent)

    fun match(
        address: Address?,
        comingFrom: List<NormalizedRelayUrl>,
    ) = address != null &&
        (isGlobal(comingFrom) || isAuthorInFollows(address)) &&
        (isHiddenList || isNotHidden(address.pubKeyHex))

    companion object {
        fun showHiddenKey(
            selectedListName: String,
            userHex: String,
        ) = selectedListName == PeopleListEvent.blockListFor(userHex) || selectedListName == MuteListEvent.blockListFor(userHex)

        fun create(
            followLists: IFeedTopNavFilter?,
            hiddenUsers: HiddenUsersState.LiveHiddenUsers,
        ): FilterByListParams =
            FilterByListParams(
                isHiddenList = followLists is MutedAuthorsByOutboxTopNavFilter || followLists is MutedAuthorsByProxyTopNavFilter,
                followLists = followLists,
                hiddenLists = hiddenUsers,
            )
    }
}
