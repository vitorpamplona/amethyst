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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.conversations.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent

class UserProfileConversationsFeedFilter(
    val user: User,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + user.pubkeyHex

    override fun feed(): List<Note> {
        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                acceptableEvent(it)
            }

        val longFormNotes =
            LocalCache.addressables.filterIntoSet { _, it ->
                acceptableEvent(it)
            }

        return sort(notes + longFormNotes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> = collection.filterTo(HashSet()) { acceptableEvent(it) }

    fun acceptableEvent(it: Note): Boolean =
        it.author == user &&
            (
                it.event is TextNoteEvent ||
                    it.event is PollNoteEvent ||
                    it.event is PollResponseEvent ||
                    it.event is ChannelMessageEvent ||
                    it.event is LiveActivitiesChatMessageEvent ||
                    it.event is CommentEvent ||
                    it.event is VoiceReplyEvent ||
                    it.event is PublicMessageEvent ||
                    it.event is TorrentCommentEvent
            ) &&
            !it.isNewThread() &&
            account.isAcceptable(it)

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit() = 200
}
