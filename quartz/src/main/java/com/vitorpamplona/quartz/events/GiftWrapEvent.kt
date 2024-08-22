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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerInternal
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

    @Deprecated(
        message = "Heavy caching was removed from this class due to high memory use. Cache it separatedly",
        replaceWith = ReplaceWith("unwrap"),
    )
    fun cachedGift(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) = unwrap(signer, onReady)

    fun unwrap(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        try {
            plainContent(signer) { giftStr ->
                val gift = fromJson(giftStr)
                if (gift is WrappedEvent) {
                    gift.host = HostStub(this.id, this.pubKey, this.kind)
                }
                innerEventId = gift.id

                onReady(gift)
            }
        } catch (e: Exception) {
            Log.w("GiftWrapEvent", "Couldn't Decrypt the content", e)
        }
    }

    private fun plainContent(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        if (content.isEmpty()) return

        signer.nip44Decrypt(content, pubKey, onReady)
    }

    fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    companion object {
        const val KIND = 1059
        const val ALT = "Encrypted event"

        fun create(
            event: Event,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.randomWithTwoDays(),
            onReady: (GiftWrapEvent) -> Unit,
        ) {
            val signer = NostrSignerInternal(KeyPair()) // GiftWrap is always a random key
            val serializedContent = toJson(event)
            val tags = arrayOf(arrayOf("p", recipientPubKey))

            signer.nip44Encrypt(serializedContent, recipientPubKey) { content ->
                signer.sign(createdAt, KIND, tags, content, onReady)
            }
        }
    }
}
