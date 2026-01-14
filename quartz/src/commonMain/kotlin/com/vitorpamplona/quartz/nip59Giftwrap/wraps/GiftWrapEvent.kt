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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip59Giftwrap.HostStub
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.utils.Log
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
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var innerEventId: HexKey? = null

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

        fun create(
            event: Event,
            recipientPubKey: HexKey,
            expirationDelta: Long? = null,
            createdAt: Long = TimeUtils.randomWithTwoDays(),
        ): GiftWrapEvent {
            val signer = NostrSignerSync(KeyPair()) // GiftWrap is always a random key

            val tags =
                expirationDelta?.let {
                    // minimum expiration is two days in the future due to the random created at
                    // this will make sure the even arrives and is not deleted because of the 2 days.
                    arrayOf(
                        PTag.assemble(recipientPubKey, null),
                        ExpirationTag.assemble(createdAt + it + TimeUtils.twoDays()),
                    )
                } ?: arrayOf(
                    PTag.assemble(recipientPubKey, null),
                )

            return signer.sign(
                createdAt = createdAt,
                kind = KIND,
                tags = tags,
                content = signer.nip44Encrypt(event.toJson(), recipientPubKey),
            )
        }
    }
}
