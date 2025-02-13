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
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip04Dm.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

object NostrChatroomDataSource : AmethystNostrDataSource("ChatroomFeed") {
    lateinit var account: Account
    private var withRoom: ChatroomKey? = null

    private val latestEOSEs = EOSEAccount()

    fun loadMessagesBetween(
        accountIn: Account,
        withRoom: ChatroomKey,
    ) {
        this.account = accountIn
        this.withRoom = withRoom
        resetFilters()
    }

    fun createMessagesToMeFilter(): TypedFilter? {
        val myPeer = withRoom

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(PrivateDmEvent.KIND),
                        authors = myPeer.users.toList(),
                        tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(withRoom.hashCode().toString())
                                ?.relayList,
                    ),
            )
        } else {
            null
        }
    }

    fun createMessagesFromMeFilter(): TypedFilter? {
        val myPeer = withRoom

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(PrivateDmEvent.KIND),
                        authors = listOf(account.userProfile().pubkeyHex),
                        tags = mapOf("p" to myPeer.users.map { it }),
                        since =
                            latestEOSEs.users[account.userProfile()]
                                ?.followList
                                ?.get(withRoom.hashCode().toString())
                                ?.relayList,
                    ),
            )
        } else {
            null
        }
    }

    fun clearEOSEs(account: Account) {
        latestEOSEs.removeDataFor(account.userProfile())
    }

    val inandoutChannel =
        requestNewChannel { time, relayUrl ->
            latestEOSEs.addOrUpdate(account.userProfile(), withRoom.hashCode().toString(), relayUrl, time)
        }

    override fun updateChannelFilters() {
        inandoutChannel.typedFilters =
            listOfNotNull(
                createMessagesToMeFilter(),
                createMessagesFromMeFilter(),
            ).ifEmpty { null }
    }
}
