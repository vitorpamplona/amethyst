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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

// This allows multiple screen to be listening to tags, even the same tag
class ChatroomQueryState(
    val room: ChatroomKey,
    val account: Account,
)

class ChatroomFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<ChatroomQueryState>(client) {
    private val latestEOSEs = EOSEAccount()

    fun createMessagesToMeFilter(key: ChatroomQueryState): TypedFilter? =
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    authors = key.room.users.toList(),
                    tags = mapOf("p" to listOf(key.account.userProfile().pubkeyHex)),
                    since =
                        latestEOSEs.users[key.account.userProfile()]
                            ?.followList
                            ?.get(key.room.hashCode().toString())
                            ?.relayList,
                ),
        )

    fun createMessagesFromMeFilter(key: ChatroomQueryState): TypedFilter? =
        if (key.room != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(PrivateDmEvent.KIND),
                        authors = listOf(key.account.userProfile().pubkeyHex),
                        tags = mapOf("p" to key.room.users.map { it }),
                        since =
                            latestEOSEs.users[key.account.userProfile()]
                                ?.followList
                                ?.get(key.room.hashCode().toString())
                                ?.relayList,
                    ),
            )
        } else {
            null
        }

    fun clearEOSEs(account: Account) {
        latestEOSEs.removeDataFor(account.userProfile())
    }

    fun mergeAllFilters(key: ChatroomQueryState): List<TypedFilter>? =
        listOfNotNull(
            createMessagesToMeFilter(key),
            createMessagesFromMeFilter(key),
        ).ifEmpty { null }

    fun newSub(key: ChatroomQueryState): Subscription =
        requestNewSubscription { time, relayUrl ->
            latestEOSEs.addOrUpdate(key.account.userProfile(), key.room.hashCode().toString(), relayUrl, time)
        }

    val userSubscriptionMap = mutableMapOf<User, String>()

    fun findOrCreateSubFor(key: ChatroomQueryState): Subscription {
        var subId = userSubscriptionMap[key.account.userProfile()]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        } else {
            getSub(subId) ?: newSub(key).also { userSubscriptionMap[key.account.userProfile()] = it.id }
        }
    }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<ChatroomQueryState>) {
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
