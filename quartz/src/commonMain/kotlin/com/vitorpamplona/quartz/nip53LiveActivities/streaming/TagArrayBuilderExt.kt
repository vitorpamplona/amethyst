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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.CurrentParticipantsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.EndsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.PinnedEventTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.RecordingTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.RelayListTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StartsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StreamingTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.TotalParticipantsTag

fun TagArrayBuilder<LiveActivitiesEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<LiveActivitiesEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<LiveActivitiesEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<LiveActivitiesEvent>.streaming(url: String) = addUnique(StreamingTag.assemble(url))

fun TagArrayBuilder<LiveActivitiesEvent>.recording(url: String) = addUnique(RecordingTag.assemble(url))

fun TagArrayBuilder<LiveActivitiesEvent>.starts(timestamp: Long) = addUnique(StartsTag.assemble(timestamp))

fun TagArrayBuilder<LiveActivitiesEvent>.ends(timestamp: Long) = addUnique(EndsTag.assemble(timestamp))

fun TagArrayBuilder<LiveActivitiesEvent>.status(status: StatusTag.STATUS) = addUnique(StatusTag.assemble(status))

fun TagArrayBuilder<LiveActivitiesEvent>.currentParticipants(count: Int) = addUnique(CurrentParticipantsTag.assemble(count))

fun TagArrayBuilder<LiveActivitiesEvent>.totalParticipants(count: Int) = addUnique(TotalParticipantsTag.assemble(count))

fun TagArrayBuilder<LiveActivitiesEvent>.participant(
    pubKey: HexKey,
    relayHint: NormalizedRelayUrl? = null,
    role: String? = null,
    proof: HexKey? = null,
) = add(ParticipantTag.assemble(pubKey, relayHint?.url, role, proof))

fun TagArrayBuilder<LiveActivitiesEvent>.participants(participants: List<ParticipantTag>) =
    participants.forEach {
        participant(it.pubKey, it.relayHint, it.role, it.proof)
    }

fun TagArrayBuilder<LiveActivitiesEvent>.relays(urls: List<NormalizedRelayUrl>) = addUnique(RelayListTag.assemble(urls))

fun TagArrayBuilder<LiveActivitiesEvent>.pinned(eventId: HexKey) = add(PinnedEventTag.assemble(eventId))
