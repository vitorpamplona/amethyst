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
package com.vitorpamplona.quartz.nip59Giftwrap.wraps

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip59Giftwrap.HostStub
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class GiftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var innerEventId: HexKey? = null

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (innerEventId?.bytesUsedInMemory() ?: 0)

    fun copyNoContent(): GiftWrapEvent {
        val copy =
            GiftWrapEvent(
                id,
                pubKey,
                createdAt,
                tags,
                "",
                sig,
            )

        copy.innerEventId = innerEventId

        return copy
    }

    override fun isContentEncoded() = true

    suspend fun unwrapThrowing(signer: NostrSigner): Event {
        val giftStr = plainContent(signer)
        val gift = fromJson(giftStr)

        if (gift is WrappedEvent) {
            gift.host = HostStub(this.id, this.pubKey, this.kind)
        }
        innerEventId = gift.id

        return gift
    }

    suspend fun unwrapOrNull(signer: NostrSigner): Event? =
        try {
            unwrapThrowing(signer)
        } catch (_: Exception) {
            Log.w("GiftWrapEvent", "Couldn't Decrypt the content " + this.toNostrUri())
            null
        }

    private suspend fun plainContent(signer: NostrSigner): String {
        if (content.isEmpty()) return ""

        return signer.nip44Decrypt(content, pubKey)
    }

    fun recipientPubKey() = tags.firstTagValue("p")

    companion object {
        const val KIND = 1059
        const val ALT = "Encrypted event"

        suspend fun create(
            event: Event,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.randomWithTwoDays(),
        ): GiftWrapEvent {
            val signer = NostrSignerInternal(KeyPair()) // GiftWrap is always a random key
            return signer.sign(
                createdAt = createdAt,
                kind = KIND,
                tags = arrayOf(arrayOf("p", recipientPubKey)),
                content = signer.nip44Encrypt(event.toJson(), recipientPubKey),
            )
        }
    }
}
