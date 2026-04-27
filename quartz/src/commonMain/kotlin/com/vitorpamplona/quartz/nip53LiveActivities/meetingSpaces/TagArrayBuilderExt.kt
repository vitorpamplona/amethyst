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
@file:JvmName("MeetingSpaceTagArrayBuilderExt")

package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.EndpointUrlTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.RelayListTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.RoomNameTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.ServiceUrlTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlin.jvm.JvmName

fun TagArrayBuilder<MeetingSpaceEvent>.room(name: String) = addUnique(RoomNameTag.assemble(name))

fun TagArrayBuilder<MeetingSpaceEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<MeetingSpaceEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<MeetingSpaceEvent>.status(status: StatusTag.STATUS) = addUnique(StatusTag.assemble(status))

fun TagArrayBuilder<MeetingSpaceEvent>.service(url: String) = addUnique(ServiceUrlTag.assemble(url))

fun TagArrayBuilder<MeetingSpaceEvent>.endpoint(url: String) = addUnique(EndpointUrlTag.assemble(url))

fun TagArrayBuilder<MeetingSpaceEvent>.starts(unixSeconds: Long) =
    addUnique(
        com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StartsTag
            .assemble(unixSeconds),
    )

fun TagArrayBuilder<MeetingSpaceEvent>.font(
    family: String,
    url: String? = null,
) = addUnique(
    com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.FontTag
        .assemble(family, url),
)

fun TagArrayBuilder<MeetingSpaceEvent>.recording(url: String) =
    addUnique(
        com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.RecordingTag
            .assemble(url),
    )

fun TagArrayBuilder<MeetingSpaceEvent>.relays(urls: List<NormalizedRelayUrl>) = addUnique(RelayListTag.assemble(urls))

fun TagArrayBuilder<MeetingSpaceEvent>.participant(
    pubKey: HexKey,
    relayHint: NormalizedRelayUrl? = null,
    role: String? = null,
    proof: HexKey? = null,
) = add(ParticipantTag.assemble(pubKey, relayHint?.url, role, proof))

fun TagArrayBuilder<MeetingSpaceEvent>.participants(participants: List<ParticipantTag>) =
    participants.forEach {
        participant(it.pubKey, it.relayHint, it.role, it.proof)
    }
