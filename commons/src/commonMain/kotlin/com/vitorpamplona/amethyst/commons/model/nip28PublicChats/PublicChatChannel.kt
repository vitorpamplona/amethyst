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
package com.vitorpamplona.amethyst.commons.model.nip28PublicChats

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import com.vitorpamplona.quartz.nip01Core.core.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHintOptional
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

    // Important to keep this long-term reference because LocalCache uses WeakReferences.
    var creationEventNote: Note? = null
    var updateEventNote: Note? = null

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
        eventNote: Note? = null,
    ) {
        this.creator = creator
        this.event = event
        this.info = event.channelInfo()

        this.infoTags = event.tags.toImmutableListOfLists()
        this.updatedMetadataAt = event.createdAt
        this.creationEventNote = eventNote

        updateChannelInfo()
    }

    fun updateChannelInfo(
        creator: User,
        event: ChannelMetadataEvent,
        eventNote: Note? = null,
    ) {
        this.creator = creator
        this.info = event.channelInfo()

        this.infoTags = event.tags.toImmutableListOfLists()
        this.updatedMetadataAt = event.createdAt
        this.updateEventNote = eventNote

        super.updateChannelInfo()
    }

    override fun toBestDisplayName(): String = info.name ?: toNEvent().toShortDisplay()

    fun summary(): String? = info.about

    fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) return creator?.info?.banner
        return info.picture ?: creator?.info?.banner
    }

    fun anyNameStartsWith(prefix: String): Boolean =
        idHex.startsWith(prefix) ||
            info.name?.contains(prefix, true) == true ||
            info.about?.contains(prefix, true) == true
}
