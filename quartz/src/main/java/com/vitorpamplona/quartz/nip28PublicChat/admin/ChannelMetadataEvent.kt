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
package com.vitorpamplona.quartz.nip28PublicChat.admin

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonParseException
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip28PublicChat.base.BasePublicChatEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelData
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelDataNorm
import com.vitorpamplona.quartz.nip28PublicChat.base.channel
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BasePublicChatEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    override fun eventHints() = channelInfo().relays?.mapNotNull { EventIdHint(id, it) } ?: emptyList()

    fun channelInfo(): ChannelDataNorm {
        return try {
            ChannelData.parse(content)?.normalize() ?: ChannelDataNorm()
        } catch (e: JsonParseException) {
            Log.e("ChannelCreateEvent", "Failure to parse ${this.toJson()}", e)
            ChannelDataNorm()
        }
    }

    companion object {
        const val KIND = 41
        const val ALT = "This is a public chat definition update"

        fun build(
            name: String?,
            about: String?,
            picture: String?,
            relays: List<NormalizedRelayUrl>?,
            channel: EventHintBundle<ChannelCreateEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMetadataEvent>.() -> Unit = {},
        ) = build(ChannelDataNorm(name, about, picture, relays), channel, createdAt, initializer)

        fun build(
            name: String?,
            about: String?,
            picture: String?,
            relays: List<NormalizedRelayUrl>?,
            channel: ETag,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMetadataEvent>.() -> Unit = {},
        ) = build(ChannelDataNorm(name, about, picture, relays), channel, createdAt, initializer)

        fun build(
            data: ChannelDataNorm,
            channel: EventHintBundle<ChannelCreateEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMetadataEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, data.toContent(), createdAt) {
            alt("Public chat update to ${data.name}")
            channel(channel)
            initializer()
        }

        fun build(
            data: ChannelDataNorm,
            channel: ETag,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelMetadataEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, data.toContent(), createdAt) {
            alt("Public chat update to ${data.name}")
            channel(channel)
            initializer()
        }
    }
}
