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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.share

import com.vitorpamplona.amethyst.commons.ui.feeds.FeedFilter
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder

/**
 * Recent private-DM conversations only (no public channels, ephemeral chats, or
 * marmot groups). Backs the Share-to-DM picker. Read-only/transient — extends the
 * non-additive [FeedFilter] base because the picker loads once and does not need
 * live additive updates.
 */
class ShareDMRoomsFeedFilter(
    val account: Account,
) : FeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    override fun feed(): List<Note> {
        val chatList = account.chatroomList
        val followingKeySet = account.followingKeySet()

        return chatList.rooms
            .mapNotNull { key, chatroom ->
                if ((chatroom.senderIntersects(followingKeySet) || chatList.hasSentMessagesTo(key)) &&
                    !account.isAllHidden(key.users)
                ) {
                    chatroom.newestMessage
                } else {
                    null
                }
            }.sortedWith(DefaultFeedOrder)
    }
}
