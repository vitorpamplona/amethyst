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
package com.vitorpamplona.amethyst.commons.ui.screen.home.dal

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.ui.dal.FilterByListParams
import com.vitorpamplona.amethyst.commons.ui.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent

open class HomeConversationsFeedFilter(
    val account: IAccount,
    val cache: ICacheProvider,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.accountSettings.defaultHomeFollowListCode.value

    override fun showHiddenKey(): Boolean = account.getHomeFollowList()?.isMutedFilter == true

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        return sort(
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, filterParams)
            },
        )
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    fun buildFilterParams(account: IAccount): FilterByListParams =
        FilterByListParams.create(
            followLists = account.getHomeFollowList(),
            hiddenUsers = account.getLiveHiddenUsers(),
        )

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableEvent(it, filterParams)
        }
    }

    fun acceptableEvent(
        event: Event?,
        relays: List<NormalizedRelayUrl>,
        filterParams: FilterByListParams,
    ): Boolean =
        (
            event is TextNoteEvent ||
                event is ZapPollEvent ||
                event is PollResponseEvent ||
                event is ChannelMessageEvent ||
                event is CommentEvent ||
                event is VoiceReplyEvent ||
                event is PublicMessageEvent ||
                event is LiveActivitiesChatMessageEvent
        ) &&
            filterParams.match(event, relays)

    fun acceptableEvent(
        note: Note,
        filterParams: FilterByListParams,
    ): Boolean = acceptableEvent(note.event, note.relays, filterParams) && !note.isNewThread()

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
