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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import kotlinx.coroutines.flow.toList
import kotlin.collections.flatten

// This allows multiple screen to be listening to tags, even the same tag
class ChatroomListState(
    val account: Account,
)

class ChatroomListFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<ChatroomListState>(client) {
    val latestEOSEs = EOSEAccount()
    val chatRoomListKey = "ChatroomList"

    fun since(key: ChatroomListState) =
        latestEOSEs.users[key.account.userProfile()]
            ?.followList
            ?.get(chatRoomListKey)
            ?.relayList

    fun newEose(
        key: ChatroomListState,
        relayUrl: String,
        time: Long,
    ) {
        latestEOSEs.addOrUpdate(
            key.account.userProfile(),
            chatRoomListKey,
            relayUrl,
            time,
        )
    }

    fun createMessagesToMeFilter(key: ChatroomListState) =
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    tags = mapOf("p" to listOf(key.account.userProfile().pubkeyHex)),
                    since = since(key),
                ),
        )

    fun createMessagesFromMeFilter(key: ChatroomListState) =
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    authors = listOf(key.account.userProfile().pubkeyHex),
                    since = since(key),
                ),
        )

    fun createChannelsCreatedbyMeFilter(key: ChatroomListState) =
        TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            ChannelCreateEvent.KIND,
                            ChannelMetadataEvent.KIND,
                        ),
                    authors = listOf(key.account.userProfile().pubkeyHex),
                    since = since(key),
                ),
        )

    fun createMyChannelsFilter(key: ChatroomListState): TypedFilter? {
        val followingEvents = key.account.publicChatList.livePublicChatEventIdSet.value

        if (followingEvents.isEmpty()) return null

        return TypedFilter(
            // Metadata comes from any relay
            types = EVENT_FINDER_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(ChannelCreateEvent.KIND),
                    ids = followingEvents.toList(),
                    since = since(key),
                ),
        )
    }

    fun createLastChannelInfoFilter(key: ChatroomListState): List<TypedFilter>? {
        val followingEvents =
            key.account.publicChatList.livePublicChatEventIdSet.value

        if (followingEvents.isEmpty()) return null

        return followingEvents.map {
            TypedFilter(
                // Metadata comes from any relay
                types = EVENT_FINDER_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ChannelMetadataEvent.KIND),
                        tags = mapOf("e" to listOf(it)),
                        since = since(key),
                        limit = 1,
                    ),
            )
        }
    }

    fun createLastMessageOfEachChannelFilter(key: ChatroomListState): List<TypedFilter>? {
        val followingEvents =
            key.account.publicChatList.livePublicChatEventIdSet.value

        if (followingEvents.isEmpty()) return null

        return followingEvents.map {
            TypedFilter(
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ChannelMessageEvent.KIND),
                        tags = mapOf("e" to listOf(it)),
                        since = since(key),
                        // Remember to consider spam that is being removed from the UI
                        limit = 50,
                    ),
            )
        }
    }

    fun createLastMessageOfEachEphemeralChatFilter(key: ChatroomListState): List<TypedFilter>? {
        val followingEvents =
            key.account.ephemeralChatList.liveEphemeralChatList.value
                .map { it.id }

        if (followingEvents.isEmpty()) return null

        return listOf(
            TypedFilter(
                // Metadata comes from any relay
                types = setOf(FeedType.PUBLIC_CHATS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(EphemeralChatEvent.KIND),
                        tags = mapOf("d" to followingEvents),
                        since = since(key),
                        // Remember to consider spam that is being removed from the UI
                        limit = 50,
                    ),
            ),
        )
    }

    fun mergeAllFilters(key: ChatroomListState): List<TypedFilter>? =
        listOfNotNull(
            listOfNotNull(
                createMessagesToMeFilter(key),
                createMessagesFromMeFilter(key),
                createMyChannelsFilter(key),
            ),
            createLastChannelInfoFilter(key),
            createLastMessageOfEachChannelFilter(key),
            createLastMessageOfEachEphemeralChatFilter(key),
        ).flatten().ifEmpty { null }

    fun newSub(key: ChatroomListState): Subscription =
        requestNewSubscription { time, relayUrl ->
            newEose(key, relayUrl, time)
        }

    val userSubscriptionMap = mutableMapOf<User, String>()

    fun findOrCreateSubFor(key: ChatroomListState): Subscription {
        var subId = userSubscriptionMap[key.account.userProfile()]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        } else {
            getSub(subId) ?: newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        }
    }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<ChatroomListState>) {
        val uniqueSubscribedAccounts = keys.distinctBy { it.account }

        val updated = mutableSetOf<User>()

        uniqueSubscribedAccounts.forEach {
            val user = it.account.userProfile()
            val sub = findOrCreateSubFor(it)
            sub.typedFilters = mergeAllFilters(it)

            updated.add(user)
        }

        userSubscriptionMap.forEach {
            if (it.key !in updated) {
                dismissSubscription(it.value)
            }
        }
    }
}
