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
package com.vitorpamplona.quartz.nip53LiveActivities.presence

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.MeetingSpaceTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.tags.HandRaisedTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.tags.MutedTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.tags.OnstageTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.tags.PublishingTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class MeetingRoomPresenceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(MeetingSpaceTag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(MeetingSpaceTag::parseAddressId)

    fun interactiveRoom() = tags.firstNotNullOfOrNull(MeetingSpaceTag::parse)

    fun handRaised() = tags.firstNotNullOfOrNull(HandRaisedTag::parse)

    fun muted() = tags.firstNotNullOfOrNull(MutedTag::parse)

    /** True when the peer is actively pushing audio packets to the relay. */
    fun publishing() = tags.firstNotNullOfOrNull(PublishingTag::parse)

    /** True when the peer holds a speaker slot (vs. pure audience). */
    fun onstage() = tags.firstNotNullOfOrNull(OnstageTag::parse)

    companion object Companion {
        const val KIND = 10312
        const val ALT = "Room Presence tag"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

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

        /**
         * Convenience builder when the parent is a kind 30312 [MeetingSpaceEvent]
         * (Clubhouse-style audio room). Mirrors the [MeetingRoomEvent] overload so
         * a participant can publish presence + hand-raise + mute against an audio
         * room without adapting to the meeting-room (kind 30313) variant.
         */
        fun build(
            root: MeetingSpaceEvent,
            handRaised: Boolean? = null,
            muted: Boolean? = null,
            publishing: Boolean? = null,
            onstage: Boolean? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MeetingRoomPresenceEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(root.room() ?: ALT)

            roomMeeting(MeetingSpaceTag(root.address(), root.relays().firstOrNull()))

            handRaised?.let { handRaised(it) }
            muted?.let { muted(it) }
            publishing?.let { publishing(it) }
            onstage?.let { onstage(it) }
            initializer()
        }
    }
}
