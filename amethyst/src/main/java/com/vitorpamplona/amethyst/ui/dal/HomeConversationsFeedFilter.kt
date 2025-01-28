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
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMessageEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesChatMessageEvent

class HomeConversationsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultHomeFollowList.value

    override fun showHiddenKey(): Boolean =
        account.settings.defaultHomeFollowList.value == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultHomeFollowList.value == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        return sort(
            LocalCache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, filterParams)
            },
        )
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.settings.defaultHomeFollowList.value,
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, filterParams)
        }
    }

    fun acceptableEvent(
        event: Event?,
        filterParams: FilterByListParams,
    ): Boolean =
        (
            event is TextNoteEvent ||
                event is PollNoteEvent ||
                event is ChannelMessageEvent ||
                event is CommentEvent ||
                event is LiveActivitiesChatMessageEvent
        ) &&
            filterParams.match(event)

    fun acceptableEvent(
        note: Note,
        filterParams: FilterByListParams,
    ): Boolean = acceptableEvent(note.event, filterParams) && !note.isNewThread()

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)
}
