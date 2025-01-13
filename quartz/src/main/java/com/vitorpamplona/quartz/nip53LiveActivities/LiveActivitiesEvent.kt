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
package com.vitorpamplona.quartz.nip53LiveActivities

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.audio.Participant
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
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
    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)

    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)

    fun streaming() = tags.firstOrNull { it.size > 1 && it[0] == "streaming" }?.get(1)

    fun starts() = tags.firstOrNull { it.size > 1 && it[0] == "starts" }?.get(1)?.toLongOrNull()

    fun ends() = tags.firstOrNull { it.size > 1 && it[0] == "ends" }?.get(1)

    fun status() = checkStatus(tags.firstOrNull { it.size > 1 && it[0] == "status" }?.get(1))

    fun currentParticipants() = tags.firstOrNull { it.size > 1 && it[0] == "current_participants" }?.get(1)

    fun totalParticipants() = tags.firstOrNull { it.size > 1 && it[0] == "total_participants" }?.get(1)

    fun participants() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(3)) }

    fun hasHost() = tags.any { it.size > 3 && it[0] == "p" && it[3].equals("Host", true) }

    fun host() = tags.firstOrNull { it.size > 3 && it[0] == "p" && it[3].equals("Host", true) }?.get(1)

    fun hosts() = tags.filter { it.size > 3 && it[0] == "p" && it[3].equals("Host", true) }.map { it[1] }

    fun checkStatus(eventStatus: String?): String? =
        if (eventStatus == STATUS_LIVE && createdAt < TimeUtils.eightHoursAgo()) {
            STATUS_ENDED
        } else {
            eventStatus
        }

    fun participantsIntersect(keySet: Set<String>): Boolean = keySet.contains(pubKey) || tags.any { it.size > 1 && it[0] == "p" && it[1] in keySet }

    companion object {
        const val KIND = 30311
        const val ALT = "Live activity event"

        const val STATUS_LIVE = "live"
        const val STATUS_PLANNED = "planned"
        const val STATUS_ENDED = "ended"

        fun create(
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (LiveActivitiesEvent) -> Unit,
        ) {
            val tags = arrayOf(arrayOf("alt", ALT))
            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}
