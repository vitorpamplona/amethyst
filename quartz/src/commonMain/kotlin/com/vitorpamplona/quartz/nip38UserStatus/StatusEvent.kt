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
package com.vitorpamplona.quartz.nip38UserStatus

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class StatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun firstTaggedUrl() = tags.firstTagValue("r")

    companion object {
        const val KIND = 30315

        const val GENERAL = "general"
        const val MUSIC = "music"

        suspend fun create(
            msg: String,
            type: String = GENERAL,
            expiration: Long? = null,
            url: String? = null,
            profileId: HexKey? = null,
            eventId: HexKey? = null,
            addressableId: String? = null,
            emojiTags: List<EmojiUrlTag>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): StatusEvent {
            val tags = mutableListOf<Array<String>>()

            tags.add(arrayOf("d", type))
            expiration?.let { tags.add(arrayOf("expiration", it.toString())) }
            url?.let { tags.add(arrayOf("r", it)) }
            profileId?.let { tags.add(PTag.assemble(it, null)) }
            eventId?.let { tags.add(ETag.assemble(it, null, null)) }
            addressableId?.let { tags.add(ATag.assemble(it, null)) }
            emojiTags?.forEach { tags.add(it.toTagArray()) }

            return signer.sign(createdAt, KIND, tags.toTypedArray(), msg)
        }

        suspend fun update(
            event: StatusEvent,
            newStatus: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): StatusEvent {
            val tags = event.tags
            return signer.sign(createdAt, KIND, tags, newStatus)
        }

        suspend fun clear(
            event: StatusEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): StatusEvent {
            val msg = ""
            val tags = event.tags.filter { it.size > 1 && it[0] == "d" }
            return signer.sign(createdAt, KIND, tags.toTypedArray(), msg)
        }
    }
}
