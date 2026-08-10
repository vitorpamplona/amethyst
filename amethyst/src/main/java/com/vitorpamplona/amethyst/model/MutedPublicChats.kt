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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent

/**
 * The channel a mute decision applies to, or null when [event] is not a public-chat
 * message.
 *
 * Deliberately NOT `threadRootIdOrSelf()`. That returns the same channel id here — a
 * NIP-28 message's NIP-10 root marker IS its channel — but it means something else
 * (the NIP-51 "muted thread" key, which HIDES content). Keeping the two apart is what
 * stops "mute notifications" and "mute thread" from bleeding into each other.
 */
fun mutedChannelIdOf(event: Event?): HexKey? = (event as? ChannelMessageEvent)?.channelId()

/** True when [event] is a public-chat message in a channel the user has silenced. */
fun isMutedPublicChatMessage(
    event: Event?,
    mutedChannels: Set<HexKey>,
): Boolean {
    if (mutedChannels.isEmpty()) return false
    val channelId = mutedChannelIdOf(event) ?: return false
    return channelId in mutedChannels
}
