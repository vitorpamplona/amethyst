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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.cord03Channels.tags.ChannelTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.EpochTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

/**
 * The Concord Chat Plane binding, added to any standard event (kind 9 chat, 7
 * reaction, 5 delete, …) that is published on a channel plane. Because the binding
 * layers onto reused standard events, these extensions are generic over the event
 * type instead of pinned to a Concord-specific one.
 */
fun <T : Event> TagArrayBuilder<T>.channel(channelId: HexKey) = addUnique(ChannelTag.assemble(channelId))

fun <T : Event> TagArrayBuilder<T>.epoch(epoch: Long) = addUnique(EpochTag.assemble(epoch))

/** Binds an event to [channelId] at [epoch] — both tags every Chat Plane rumor carries. */
fun <T : Event> TagArrayBuilder<T>.channelBinding(
    channelId: HexKey,
    epoch: Long,
) = apply {
    channel(channelId)
    epoch(epoch)
}
