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
package com.vitorpamplona.quartz.nip28PublicChat.admin

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTags
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEventIds
import com.vitorpamplona.quartz.nip28PublicChat.base.BasePublicChatEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.channel
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelHideMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BasePublicChatEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    SearchableEvent {
    // NIP-28: the content is the moderator's human-written reason for hiding.
    override fun indexableContent() = content

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    /**
     * NIP-28 names the channel only through a MARKED root (Quartz writes one; the spec's own 43
     * has none): the unmarked-root fallback of [BasePublicChatEvent] would read the first hidden
     * message as the channel.
     */
    override fun channel() = markedRoot()

    override fun channelId() = channel()?.eventId

    /** The hidden messages: every `e` except the channel it is posted in. */
    fun eventsToHide(): List<HexKey> {
        val channel = channelId()
        return tags.taggedEventIds().filter { it != channel }
    }

    companion object {
        const val KIND = 43

        fun build(
            reason: String,
            messagesToHide: List<ETag>,
            channel: EventHintBundle<ChannelCreateEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelHideMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, reason, createdAt) {
            channel(channel)
            eTags(messagesToHide)
            initializer()
        }
    }
}
