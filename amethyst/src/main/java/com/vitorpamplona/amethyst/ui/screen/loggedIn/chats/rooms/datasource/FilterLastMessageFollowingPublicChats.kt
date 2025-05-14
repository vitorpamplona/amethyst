/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource

import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import kotlin.collections.toList

fun filterLastMessageFollowingPublicChats(
    followingChannels: Set<HexKey>?,
    since: Map<String, EOSETime>?,
): List<TypedFilter>? {
    if (followingChannels == null || followingChannels.isEmpty()) return null

    return listOf(
        TypedFilter(
            // Metadata comes from any relay
            types = EVENT_FINDER_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(ChannelMetadataEvent.KIND),
                    tags = mapOf("e" to followingChannels.toList()),
                    since = since,
                    limit = 1,
                ),
        ),
        TypedFilter(
            types = setOf(FeedType.PUBLIC_CHATS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(ChannelMessageEvent.KIND),
                    tags = mapOf("e" to followingChannels.toList()),
                    since = since,
                    // Remember to consider spam that is being removed from the UI
                    limit = 100,
                ),
        ),
    )
}
