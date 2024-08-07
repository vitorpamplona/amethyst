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
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.PerRelayFilter
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent

object NostrChatroomListDataSource : AmethystNostrDataSource("MailBoxFeed") {
    lateinit var account: Account

    val latestEOSEs = EOSEAccount()
    val chatRoomList = "ChatroomList"

    fun createMessagesToMeFilter() =
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                PerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(chatRoomList)
                            ?.relayList,
                ),
        )

    fun createMessagesFromMeFilter() =
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                PerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(chatRoomList)
                            ?.relayList,
                ),
        )

    fun createChannelsCreatedbyMeFilter() =
        TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                PerRelayFilter(
                    kinds = listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(chatRoomList)
                            ?.relayList,
                ),
        )

    fun createMyChannelsFilter(): TypedFilter? {
        val followingEvents = account.selectedChatsFollowList()

        if (followingEvents.isEmpty()) return null

        return TypedFilter(
            // Metadata comes from any relay
            types = EVENT_FINDER_TYPES,
            filter =
                PerRelayFilter(
                    kinds = listOf(ChannelCreateEvent.KIND),
                    ids = followingEvents.toList(),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(chatRoomList)
                            ?.relayList,
                ),
        )
    }

    fun createLastChannelInfoFilter(): List<TypedFilter>? {
        val followingEvents = account.selectedChatsFollowList()

        if (followingEvents.isEmpty()) return null

        return followingEvents.map {
            TypedFilter(
                // Metadata comes from any relay
                types = EVENT_FINDER_TYPES,
                filter =
                    PerRelayFilter(
                        kinds = listOf(ChannelMetadataEvent.KIND),
                        tags = mapOf("e" to listOf(it)),
                        limit = 1,
                    ),
            )
        }
    }

    fun createLastMessageOfEachChannelFilter(): List<TypedFilter>? {
        val followingEvents = account.selectedChatsFollowList()

        if (followingEvents.isEmpty()) return null

        return followingEvents.map {
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    PerRelayFilter(
                        kinds = listOf(ChannelMessageEvent.KIND),
                        tags = mapOf("e" to listOf(it)),
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(chatRoomList)
                                ?.relayList,
                        // Remember to consider spam that is being removed from the UI
                        limit = 50,
                    ),
            )
        }
    }

    val chatroomListChannel =
        requestNewChannel { time, relayUrl ->
            latestEOSEs.addOrUpdate(account.userProfile(), chatRoomList, relayUrl, time)
        }

    override fun updateChannelFilters() {
        val list =
            listOfNotNull(
                createMessagesToMeFilter(),
                createMessagesFromMeFilter(),
                createMyChannelsFilter(),
            )

        chatroomListChannel.typedFilters =
            listOfNotNull(
                list,
                createLastChannelInfoFilter(),
                createLastMessageOfEachChannelFilter(),
            ).flatten()
                .ifEmpty { null }
    }
}
