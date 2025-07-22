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
package com.vitorpamplona.amethyst.model.nip28PublicChats

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.note.toShortDisplay
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHintOptional
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelDataNorm

@Stable
class PublicChatChannel(
    val idHex: String,
) : Channel() {
    var creator: User? = null
    var event: ChannelCreateEvent? = null

    var info = ChannelDataNorm(null, null, null, null)
    var infoTags = EmptyTagList
    var updatedMetadataAt: Long = 0

    override fun relays() = info.relays?.toSet() ?: super.relays()

    fun relayHintUrls() = relays().take(3)

    fun relayHintUrl() = relays().firstOrNull()

    fun toNEvent() = NEvent.create(idHex, event?.pubKey, ChannelCreateEvent.KIND, relayHintUrls())

    fun toNostrUri() = "nostr:${toNEvent()}"

    fun toEventHint() = event?.let { EventHintBundle(it, relayHintUrl(), null) }

    fun toEventId() = EventIdHintOptional(idHex, relayHintUrl())

    fun updateChannelInfo(
        creator: User,
        event: ChannelCreateEvent,
    ) {
        this.creator = creator
        this.event = event

        this.info = event.channelInfo()
        this.infoTags = event.tags.toImmutableListOfLists()
        this.updatedMetadataAt = event.createdAt

        updateChannelInfo()
    }

    fun updateChannelInfo(
        creator: User,
        event: ChannelMetadataEvent,
    ) {
        this.creator = creator

        this.info = event.channelInfo()
        this.infoTags = event.tags.toImmutableListOfLists()
        this.updatedMetadataAt = event.createdAt

        super.updateChannelInfo()
    }

    override fun toBestDisplayName(): String = info.name ?: toNEvent().toShortDisplay()

    fun summary(): String? = info.about

    fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) return creator?.info?.banner
        return info.picture ?: creator?.info?.banner
    }

    override fun anyNameStartsWith(prefix: String): Boolean =
        idHex.startsWith(prefix) ||
            info.name?.contains(prefix, true) == true ||
            info.about?.contains(prefix, true) == true
}
