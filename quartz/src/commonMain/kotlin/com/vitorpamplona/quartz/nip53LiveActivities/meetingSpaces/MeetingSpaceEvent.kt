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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.EndpointUrlTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.RelayListTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.RoomNameTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.ServiceUrlTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class MeetingSpaceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(ParticipantTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(ParticipantTag::parseKey)

    fun room() = tags.firstNotNullOfOrNull(RoomNameTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun status() = checkStatus(tags.firstNotNullOfOrNull(StatusTag::parseEnum))

    fun isLive() = status() != StatusTag.STATUS.CLOSED

    fun service() = tags.firstNotNullOfOrNull(ServiceUrlTag::parse)

    fun endpoint() = tags.firstNotNullOfOrNull(EndpointUrlTag::parse)

    /**
     * Theme tints — every `["c", hex, "background"|"text"|"primary"]`
     * tag, in event order. Clients use the first hex per target;
     * extras are spec-defined as fallbacks for clients with palette
     * support (out of scope for v1).
     */
    fun colors() = tags.mapNotNull(com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.ColorTag::parse)

    /**
     * Background image / pattern for the room screen. Returns the
     * first valid `["bg", url, mode]` tag.
     */
    fun background() = tags.firstNotNullOfOrNull(com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.BackgroundTag::parse)

    /**
     * Suggested typography for the room screen. Returns the first
     * `["f", family, optionalUrl]` tag. Clients with theming
     * support match the family against system fonts (or fetch the
     * URL); clients without theming support ignore.
     */
    fun font() = tags.firstNotNullOfOrNull(com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.FontTag::parse)

    /**
     * Optional URL to a post-room recording. Hosts that record a
     * room re-publish the kind-30312 with `status=closed` and
     * `["recording", url]` so audience members who missed the live
     * session can listen back. Returns null when no recording is
     * available.
     */
    fun recording() = tags.firstNotNullOfOrNull(com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.RecordingTag::parse)

    /**
     * Scheduled start time as unix seconds, only meaningful when
     * [status] is [StatusTag.STATUS.PLANNED]. Returns null on
     * malformed or absent tag — the room-list renderer falls back
     * to "live now" for status=OPEN/PRIVATE rooms.
     */
    fun starts() = tags.firstNotNullOfOrNull(com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StartsTag::parse)

    fun relays() = tags.mapNotNull(RelayListTag::parse).flatten()

    fun allRelayUrls() = tags.mapNotNull(RelayListTag::parse).flatten()

    fun participantKeys(): List<HexKey> = tags.mapNotNull(ParticipantTag::parseKey)

    fun participants() = tags.mapNotNull(ParticipantTag::parse)

    fun checkStatus(eventStatus: StatusTag.STATUS?): StatusTag.STATUS? =
        if (eventStatus != StatusTag.STATUS.CLOSED && createdAt < TimeUtils.eightHoursAgo()) {
            StatusTag.STATUS.CLOSED
        } else {
            eventStatus
        }

    fun participantsIntersect(keySet: Set<String>): Boolean = keySet.contains(pubKey) || tags.any(ParticipantTag::isIn, keySet)

    companion object Companion {
        const val KIND = 30312
        const val ALT = "Interactive room event"

        suspend fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): MeetingSpaceEvent {
            val tags = arrayOf(AltTag.assemble(ALT))
            return signer.sign(createdAt, KIND, tags, "")
        }

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            room: String,
            status: StatusTag.STATUS,
            service: String,
            host: ParticipantTag,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MeetingSpaceEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            room(room)
            status(status)
            service(service)
            participant(host.pubKey, host.relayHint, host.role, host.proof)
            alt(ALT)
            initializer()
        }
    }
}
