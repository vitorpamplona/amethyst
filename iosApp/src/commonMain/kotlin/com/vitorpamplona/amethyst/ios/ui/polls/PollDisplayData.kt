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
package com.vitorpamplona.amethyst.ios.ui.polls

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/**
 * A single poll option with vote tally.
 */
data class PollOptionData(
    val index: Int,
    val descriptor: String,
    val voteCount: Int = 0,
    val percentage: Float = 0f,
)

/**
 * Display data for a poll card (NIP-88 / ZapPoll, kind 6969).
 */
data class PollDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val question: String,
    val options: List<PollOptionData>,
    val totalVotes: Int = 0,
    val closedAt: Long? = null,
    val isClosed: Boolean = false,
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a ZapPollEvent to PollDisplayData.
 */
fun Note.toPollDisplayData(cache: IosLocalCache? = null): PollDisplayData? {
    val event = this.event as? ZapPollEvent ?: return null
    val user = cache?.getUserIfExists(event.pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                event.pubKey.hexToByteArrayOrNull()?.toNpub() ?: event.pubKey.take(16) + "..."
            } catch (e: Exception) {
                event.pubKey.take(16) + "..."
            }

    val pictureUrl = user?.profilePicture()

    // Tally zap votes per option (zap request notes with poll_option tag)
    val voteTallies = mutableMapOf<Int, Int>()
    for ((zapRequest, _) in zaps) {
        val zapRequestEvent = zapRequest.event ?: continue
        for (tag in zapRequestEvent.tags) {
            if (tag.size >= 2 && tag[0] == "poll_option") {
                val optionIndex = tag[1].toIntOrNull()
                if (optionIndex != null) {
                    voteTallies[optionIndex] = (voteTallies[optionIndex] ?: 0) + 1
                }
            }
        }
    }

    val totalVotes = voteTallies.values.sum()
    val closedAt = event.closedAt()
    val now =
        com.vitorpamplona.quartz.utils
            .currentTimeSeconds()

    val options =
        event.pollOptionsArray().map { opt ->
            val count = voteTallies[opt.index] ?: 0
            PollOptionData(
                index = opt.index,
                descriptor = opt.descriptor,
                voteCount = count,
                percentage = if (totalVotes > 0) count.toFloat() / totalVotes else 0f,
            )
        }

    return PollDisplayData(
        id = event.id,
        pubKeyHex = event.pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = pictureUrl,
        question = event.content,
        options = options,
        totalVotes = totalVotes,
        closedAt = closedAt,
        isClosed = closedAt != null && closedAt <= now,
        createdAt = event.createdAt,
    )
}
