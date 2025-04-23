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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent

// This allows multiple screen to be listening to tags, even the same tag
class ChannelQueryState(
    var channel: Channel,
    var account: Account,
)

class ChannelFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<ChannelQueryState>(client) {
    companion object {
        val RELAY_SET =
            setOf(
                FeedType.FOLLOWS,
                FeedType.PRIVATE_DMS,
                FeedType.GLOBAL,
                FeedType.SEARCH,
            )

        val PUBLIC_CHAT_LIST = listOf(ChannelMessageEvent.KIND)
        val LIVE_ACTIVITY_LIST = listOf(LiveActivitiesChatMessageEvent.KIND)
    }

    private val latestEOSEs = EOSEAccount()

    fun createMessagesByMeToChannelFilter(key: ChannelQueryState): TypedFilter? =
        when (key.channel) {
            is PublicChatChannel ->
                TypedFilter(
                    types = RELAY_SET,
                    filter =
                        SincePerRelayFilter(
                            kinds = PUBLIC_CHAT_LIST,
                            authors = listOf(key.account.userProfile().pubkeyHex),
                            limit = 50,
                        ),
                )
            is LiveActivitiesChannel ->
                TypedFilter(
                    types = RELAY_SET,
                    filter =
                        SincePerRelayFilter(
                            kinds = LIVE_ACTIVITY_LIST,
                            authors = listOf(key.account.userProfile().pubkeyHex),
                            limit = 50,
                        ),
                )
            else -> {
                null
            }
        }

    fun createMessagesToChannelFilter(key: ChannelQueryState): TypedFilter? {
        return when (key.channel) {
            is PublicChatChannel ->
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(ChannelMessageEvent.KIND),
                            tags = mapOf("e" to listOfNotNull(key.channel.idHex)),
                            limit = 200,
                        ),
                )
            is LiveActivitiesChannel ->
                TypedFilter(
                    types = setOf(FeedType.PUBLIC_CHATS),
                    filter =
                        SincePerRelayFilter(
                            kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                            tags = mapOf("a" to listOfNotNull(key.channel.idHex)),
                            limit = 200,
                        ),
                )
            else -> {
                null
            }
        }
        return null
    }

    fun mergeAllFilters(key: ChannelQueryState): List<TypedFilter>? =
        listOfNotNull(
            createMessagesToChannelFilter(key),
            createMessagesByMeToChannelFilter(key),
        ).ifEmpty { null }

    fun newSub(key: ChannelQueryState): Subscription =
        requestNewSubscription { time, relayUrl ->
            latestEOSEs.addOrUpdate(key.account.userProfile(), key.channel.idHex, relayUrl, time)
        }

    val userSubscriptionMap = mutableMapOf<User, String>()

    fun findOrCreateSubFor(key: ChannelQueryState): Subscription {
        var subId = userSubscriptionMap[key.account.userProfile()]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        } else {
            getSub(subId) ?: newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        }
    }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<ChannelQueryState>) {
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
