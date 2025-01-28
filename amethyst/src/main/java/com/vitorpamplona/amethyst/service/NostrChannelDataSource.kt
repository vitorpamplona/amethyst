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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesChatMessageEvent

object NostrChannelDataSource : AmethystNostrDataSource("ChatroomFeed") {
    var account: Account? = null
    var channel: Channel? = null

    fun loadMessagesBetween(
        account: Account,
        channel: Channel,
    ) {
        this.account = account
        this.channel = channel
        resetFilters()
    }

    fun clear() {
        account = null
        channel = null
    }

    fun createMessagesByMeToChannelFilter(): TypedFilter? {
        val myAccount = account ?: return null

        if (channel is PublicChatChannel) {
            // Brings on messages by the user from all other relays.
            // Since we ship with write to public, read from private only
            // this guarantees that messages from the author do not disappear.
            return TypedFilter(
                types = setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS, FeedType.GLOBAL, FeedType.SEARCH),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ChannelMessageEvent.KIND),
                        authors = listOf(myAccount.userProfile().pubkeyHex),
                        limit = 50,
                    ),
            )
        } else if (channel is LiveActivitiesChannel) {
            // Brings on messages by the user from all other relays.
            // Since we ship with write to public, read from private only
            // this guarantees that messages from the author do not disappear.
            return TypedFilter(
                types = setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS, FeedType.GLOBAL, FeedType.SEARCH),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                        authors = listOf(myAccount.userProfile().pubkeyHex),
                        limit = 50,
                    ),
            )
        }
        return null
    }

    fun createMessagesToChannelFilter(): TypedFilter? {
        if (channel is PublicChatChannel) {
            return TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ChannelMessageEvent.KIND),
                        tags = mapOf("e" to listOfNotNull(channel?.idHex)),
                        limit = 200,
                    ),
            )
        } else if (channel is LiveActivitiesChannel) {
            return TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                        tags = mapOf("a" to listOfNotNull(channel?.idHex)),
                        limit = 200,
                    ),
            )
        }
        return null
    }

    val messagesChannel = requestNewChannel()

    override fun updateChannelFilters() {
        messagesChannel.typedFilters =
            listOfNotNull(
                createMessagesToChannelFilter(),
                createMessagesByMeToChannelFilter(),
            ).ifEmpty { null }
    }
}
