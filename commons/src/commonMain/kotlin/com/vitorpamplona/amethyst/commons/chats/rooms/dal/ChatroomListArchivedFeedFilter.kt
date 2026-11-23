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
package com.vitorpamplona.amethyst.commons.chats.rooms.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType

/**
 * The Messages "Archived" tab: the Marmot chats this account archived (`MarmotChatSettings`), the
 * newest first. Archiving only moves a chat here; it stays a member and keeps receiving messages.
 */
class ChatroomListArchivedFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    override fun feed(): List<Note> {
        if (!account.chatFeedToggles.isEnabled(ChatFeedType.MARMOT)) return emptyList()
        val archived = account.marmotChatSettings.state.value.archived
        if (archived.isEmpty()) return emptyList()
        return archived
            .mapNotNull { id -> account.marmotGroupList.rooms.get(id) }
            .map { chatroom -> chatroom.newestMessage ?: chatroom.placeholderNote() }
            .sortedByDefaultFeedOrder()
    }

    // Marmot rows never come through LocalCache's additive path: the feed is rebuilt when the
    // group list or the settings change (AccountFeedContentStates).
    override fun applyFilter(newItems: Set<Note>): Set<Note> = emptySet()

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
