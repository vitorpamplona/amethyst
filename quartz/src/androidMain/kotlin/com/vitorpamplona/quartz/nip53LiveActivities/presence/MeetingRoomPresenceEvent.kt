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
package com.vitorpamplona.quartz.nip53LiveActivities.presence

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.MeetingSpaceTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.tags.HandRaisedTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class MeetingRoomPresenceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(MeetingSpaceTag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(MeetingSpaceTag::parseAddressId)

    fun interactiveRoom() = tags.firstNotNullOfOrNull(MeetingSpaceTag::parse)

    fun handRaised() = tags.firstNotNullOfOrNull(HandRaisedTag::parse)

    companion object Companion {
        const val KIND = 10312
        const val ALT = "Room Presence tag"

        fun createAddress(
            pubKey: HexKey,
            dtag: String,
        ): Address = Address(KIND, pubKey, dtag)

        fun createAddressATag(
            pubKey: HexKey,
            dtag: String,
        ): ATag = ATag(KIND, pubKey, dtag, null)

        fun createAddressTag(
            pubKey: HexKey,
            dtag: String,
        ): String = Address.assemble(KIND, pubKey, dtag)

        fun build(
            root: MeetingRoomEvent,
            handRaised: Boolean?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MeetingRoomPresenceEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(root.title() ?: ALT)

            roomMeeting(MeetingSpaceTag(root.address(), root.relays().firstOrNull()))

            handRaised?.let {
                handRaised(it)
            }
            initializer()
        }
    }
}
