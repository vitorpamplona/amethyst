/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.CurrentParticipantsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.EndsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.RelayListTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StartsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StreamingTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.TotalParticipantsTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class LiveActivitiesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun streaming() = tags.firstNotNullOfOrNull(StreamingTag::parse)

    fun starts() = tags.firstNotNullOfOrNull(StartsTag::parse)

    fun ends() = tags.firstNotNullOfOrNull(EndsTag::parse)

    fun status() = checkStatus(tags.firstNotNullOfOrNull(StatusTag::parse))

    fun currentParticipants() = tags.firstNotNullOfOrNull(CurrentParticipantsTag::parse)

    fun totalParticipants() = tags.firstNotNullOfOrNull(TotalParticipantsTag::parse)

    fun participants() = tags.mapNotNull(ParticipantTag::parse)

    fun relays() = tags.mapNotNull(RelayListTag::parse)

    fun allRelayUrls() = tags.mapNotNull(RelayListTag::parse).map { it.relayUrls }.flatten()

    fun hasHost() = tags.any(ParticipantTag::isHost)

    fun host() = tags.firstNotNullOfOrNull(ParticipantTag::parseHost)

    fun hosts() = tags.mapNotNull(ParticipantTag::parseHost)

    fun checkStatus(eventStatus: String?): String? =
        if (eventStatus == StatusTag.STATUS.LIVE.code && createdAt < TimeUtils.eightHoursAgo()) {
            StatusTag.STATUS.ENDED.code
        } else {
            eventStatus
        }

    fun participantsIntersect(keySet: Set<String>): Boolean = keySet.contains(pubKey) || tags.any(ParticipantTag::isIn, keySet)

    companion object {
        const val KIND = 30311
        const val ALT = "Live activity event"

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (LiveActivitiesEvent) -> Unit,
        ) {
            val tags = arrayOf(AltTag.assemble(ALT))
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
