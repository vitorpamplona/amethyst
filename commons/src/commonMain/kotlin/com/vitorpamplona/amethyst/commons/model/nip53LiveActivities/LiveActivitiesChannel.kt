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
package com.vitorpamplona.amethyst.commons.model.nip53LiveActivities

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent

@Stable
class LiveActivitiesChannel(
    val address: Address,
) : Channel() {
    var creator: User? = null
    var info: LiveActivitiesEvent? = null

    // Important to keep this long-term reference because LocalCache uses WeakReferences.
    var infoNote: Note? = null

    fun address() = address

    override fun relays() = info?.allRelayUrls()?.toSet()?.ifEmpty { null } ?: super.relays()

    fun relayHintUrl() = relays().firstOrNull()

    fun relayHintUrls() = relays().take(3)

    fun updateChannelInfo(
        creator: User,
        channelInfo: LiveActivitiesEvent,
        channelInfoNote: Note,
    ) {
        this.info = channelInfo
        this.creator = creator
        this.infoNote = channelInfoNote
        super.updateChannelInfo()
    }

    override fun toBestDisplayName(): String = info?.title() ?: creatorName() ?: toNAddr().toShortDisplay()

    fun creatorName(): String? = creator?.toBestDisplayName()

    fun summary(): String? = info?.summary()

    fun profilePicture(): String? = info?.image()?.ifBlank { null }

    fun anyNameStartsWith(prefix: String): Boolean =
        info?.title()?.contains(prefix, true) == true ||
            info?.summary()?.contains(prefix, true) == true

    fun toNAddr() = NAddress.create(address.kind, address.pubKeyHex, address.dTag, relayHintUrls())

    fun toATag() = ATag(address, relayHintUrl())

    fun toNostrUri() = "nostr:${toNAddr()}"
}
