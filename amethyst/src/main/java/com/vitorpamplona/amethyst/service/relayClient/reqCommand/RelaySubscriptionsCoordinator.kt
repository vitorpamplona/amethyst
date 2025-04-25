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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.ChannelFinderFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.datasource.CommunityFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.datasource.GeoHashFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.datasource.HashtagFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.datasource.UserProfileFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoFilterAssembler
import com.vitorpamplona.ammolite.relays.NostrClient
import kotlinx.coroutines.CoroutineScope

class RelaySubscriptionsCoordinator(
    cache: LocalCache,
    client: NostrClient,
    scope: CoroutineScope,
) {
    // main one: notifications, dms and account settings
    val account = AccountFilterAssembler(client)

    // always running, feed assemblers.
    val home = HomeFilterAssembler(client)
    val chatroomList = ChatroomListFilterAssembler(client)
    val video = VideoFilterAssembler(client)
    val discovery = DiscoveryFilterAssembler(client)

    // loaders of content that is not yet in the device.
    // they are active when looking at events, users, channels.
    val channelFinder = ChannelFinderFilterAssembler(client)
    val eventFinder = EventFinderFilterAssembler(client)
    val userFinder = UserFinderFilterAssembler(client)

    // active when searching or tagging users.
    val search = SearchFilterAssembler(cache, client, scope)

    // active depending on the screen.
    val channel = ChannelFilterAssembler(client)
    val chatroom = ChatroomFilterAssembler(client)
    val community = CommunityFilterAssembler(client)
    val thread = ThreadFilterAssembler(client)
    val profile = UserProfileFilterAssembler(client)
    val hashtags = HashtagFilterAssembler(client)
    val geohashes = GeoHashFilterAssembler(client)

    // active when sending zaps via NWC
    val nwc = NWCPaymentFilterAssembler(client)

    val all =
        listOf(
            account,
            home,
            chatroomList,
            video,
            discovery,
            channelFinder,
            eventFinder,
            userFinder,
            search,
            channel,
            chatroom,
            community,
            thread,
            profile,
            hashtags,
            geohashes,
            nwc,
        )

    fun start() = all.forEach { it.start() }

    fun stop() = all.forEach { it.stop() }

    fun destroy() = all.forEach { it.destroy() }

    fun printCounters() = all.forEach { it.stats.printCounter() }
}
