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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.subassemblies

import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Subscribes to the ephemeral kind-20000 geohash chat for [channel]'s cell on the
 * relays nearest the cell center. Own messages carry the same `g` tag, so this one
 * filter also brings back what the user just sent — there is no separate
 * "from user" filter (the throwaway per-cell identity would not match the account
 * pubkey the shared FromUser assembler filters by).
 */
fun filterMessagesToGeohashChat(
    channel: GeohashChatChannel,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> =
    channel.relays().map {
        RelayBasedFilter(
            relay = it,
            filter =
                Filter(
                    kinds = listOf(GeohashChatEvent.KIND),
                    tags = mapOf("g" to listOf(channel.geohash)),
                    limit = 200,
                    since = since?.get(it)?.time,
                ),
        )
    }
