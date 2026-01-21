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
package com.vitorpamplona.quartz.nip59Giftwrap.seals

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip59Giftwrap.HostStub
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class SealedRumorEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var innerEventId: HexKey? = null

    fun copyNoContent(): SealedRumorEvent {
        val copy =
            SealedRumorEvent(
                id,
                pubKey,
                createdAt,
                tags,
                "",
                sig,
            )

        copy.host = host
        copy.innerEventId = innerEventId

        return copy
    }

    override fun isContentEncoded() = true

    suspend fun unsealThrowing(signer: NostrSigner): Event {
        val rumor = Rumor.fromJson(plainContent(signer))

        val event = rumor.mergeWith(this)
        if (event is WrappedEvent) {
            event.host = host ?: HostStub(this.id, this.pubKey, this.kind)
        }
        innerEventId = event.id

        return event
    }

    suspend fun unsealOrNull(signer: NostrSigner): Event? =
        try {
            unsealThrowing(signer)
        } catch (e: Exception) {
            Log.w("RumorEvent", "Fail to decrypt or parse Rumor", e)
            null
        }

    private suspend fun plainContent(signer: NostrSigner): String {
        if (content.isEmpty()) return ""

        return signer.nip44Decrypt(content, pubKey)
    }

    companion object {
        const val KIND = 13

        suspend fun create(
            event: Event,
            encryptTo: HexKey,
            signer: NostrSigner,
            expirationDelta: Long? = null,
            createdAt: Long = TimeUtils.now(),
        ): SealedRumorEvent {
            val rumor = Rumor.create(event)
            return create(rumor, encryptTo, signer, expirationDelta, createdAt)
        }

        suspend fun create(
            rumor: Rumor,
            encryptTo: HexKey,
            signer: NostrSigner,
            expirationDelta: Long? = null,
            createdAt: Long = TimeUtils.randomWithTwoDays(),
        ): SealedRumorEvent {
            val msg = Rumor.toJson(rumor)

            val tags =
                expirationDelta?.let {
                    // minimum expiration is two days in the future due to the random created at
                    // this will make sure the even arrives and is not deleted because of the 2 days.
                    arrayOf(ExpirationTag.assemble(createdAt + it + TimeUtils.twoDays()))
                } ?: emptyArray()

            return signer.sign(
                createdAt = createdAt,
                kind = KIND,
                tags = tags,
                content = signer.nip44Encrypt(msg, encryptTo),
            )
        }
    }
}
