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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GiftWrapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient private var cachedInnerEvent: Map<HexKey, Event?> = mapOf()

    override fun isContentEncoded() = true

    fun preCachedGift(signer: NostrSigner): Event? {
        return cachedInnerEvent[signer.pubKey]
    }

    fun addToCache(
        pubKey: HexKey,
        gift: Event,
    ) {
        cachedInnerEvent = cachedInnerEvent + Pair(pubKey, gift)
    }

    fun cachedGift(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        cachedInnerEvent[signer.pubKey]?.let {
            onReady(it)
            return
        }
        unwrap(signer) { gift ->
            if (gift is WrappedEvent) {
                gift.host = this
            }
            addToCache(signer.pubKey, gift)

            onReady(gift)
        }
    }

    private fun unwrap(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        try {
            plainContent(signer) { onReady(fromJson(it)) }
        } catch (e: Exception) {
            // Log.e("UnwrapError", "Couldn't Decrypt the content", e)
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
            createdAt: Long = TimeUtils.randomWithinAWeek(),
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
