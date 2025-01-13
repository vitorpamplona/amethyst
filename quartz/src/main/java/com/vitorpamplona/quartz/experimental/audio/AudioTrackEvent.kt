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
package com.vitorpamplona.quartz.experimental.audio

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AudioTrackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun participants() = tags.filter { it.size > 1 && it[0] == "p" }.map { Participant(it[1], it.getOrNull(2)) }

    fun type() = tags.firstOrNull { it.size > 1 && it[0] == TYPE }?.get(1)

    fun price() = tags.firstOrNull { it.size > 1 && it[0] == PRICE }?.get(1)

    fun cover() = tags.firstOrNull { it.size > 1 && it[0] == COVER }?.get(1)

    // fun subject() = tags.firstOrNull { it.size > 1 && it[0] == SUBJECT }?.get(1)
    fun media() = tags.firstOrNull { it.size > 1 && it[0] == MEDIA }?.get(1)

    companion object {
        const val KIND = 31337
        const val ALT = "Audio track"

        private const val TYPE = "c"
        private const val PRICE = "price"
        private const val COVER = "cover"
        private const val SUBJECT = "subject"
        private const val MEDIA = "media"

        fun create(
            type: String,
            media: String,
            price: String? = null,
            cover: String? = null,
            subject: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (AudioTrackEvent) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    arrayOf(MEDIA, media),
                    arrayOf(TYPE, type),
                    price?.let { arrayOf(PRICE, it) },
                    cover?.let { arrayOf(COVER, it) },
                    subject?.let { arrayOf(SUBJECT, it) },
                    arrayOf("alt", ALT),
                ).toTypedArray()

            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }
}

@Immutable data class Participant(
    val key: String,
    val role: String?,
)
