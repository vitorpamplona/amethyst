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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * A NIP-29 relay-based group ("channel" in Discord terms). Unlike a NIP-C7
 * [com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel] — a
 * bare relay+room with no server-owned metadata — a relay group has a
 * relay-signed metadata event (kind 39000) carrying its name, picture, about and
 * status flags, so this channel is metadata-backed like a NIP-28
 * [com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel].
 *
 * It is keyed by [GroupId] (host relay + group id): the group only exists on its
 * host relay, so every read and write is pinned there via [relays].
 */
@Stable
class RelayGroupChannel(
    val groupId: GroupId,
) : Channel() {
    /** The latest relay-signed kind 39000 metadata event, when known. */
    var event: GroupMetadataEvent? = null

    /** Long-term reference to the metadata note (LocalCache holds weak references). */
    var metadataNote: Note? = null

    var updatedMetadataAt: Long = 0

    /** A relay group lives on exactly one relay: its host. */
    override fun relays() = setOf(groupId.relayUrl)

    override fun toBestDisplayName(): String = event?.name() ?: groupId.id

    fun summary(): String? = event?.about()

    fun profilePicture(): String? = event?.picture()

    fun isPrivate(): Boolean = event?.isPrivate() ?: false

    fun isRestricted(): Boolean = event?.isRestricted() ?: false

    fun isClosed(): Boolean = event?.isClosed() ?: false

    fun isHidden(): Boolean = event?.isHidden() ?: false

    fun hasLivekit(): Boolean = event?.hasLivekit() ?: false

    fun updateGroupInfo(
        event: GroupMetadataEvent,
        eventNote: Note? = null,
    ) {
        // Only newer metadata supersedes.
        if (event.createdAt < updatedMetadataAt) return
        this.event = event
        this.metadataNote = eventNote
        this.updatedMetadataAt = event.createdAt
        updateChannelInfo()
    }

    fun anyNameStartsWith(prefix: String): Boolean =
        groupId.id.contains(prefix, true) ||
            event?.name()?.contains(prefix, true) == true ||
            event?.about()?.contains(prefix, true) == true
}
