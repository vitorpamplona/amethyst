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
package com.vitorpamplona.quartz.nip59Giftwrap

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class SealedGossipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Transient var innerEventId: HexKey? = null

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (innerEventId?.bytesUsedInMemory() ?: 0)

    fun copyNoContent(): SealedGossipEvent {
        val copy =
            SealedGossipEvent(
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

    @Deprecated(
        message = "Heavy caching was removed from this class due to high memory use. Cache it separatedly",
        replaceWith = ReplaceWith("unseal"),
    )
    fun cachedGossip(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) = unseal(signer, onReady)

    fun unseal(
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        try {
            plainContent(signer) {
                try {
                    val gossip = Gossip.fromJson(it)
                    val event = gossip.mergeWith(this)
                    if (event is WrappedEvent) {
                        event.host = host ?: HostStub(this.id, this.pubKey, this.kind)
                    }
                    innerEventId = event.id

                    onReady(event)
                } catch (e: Exception) {
                    Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
                }
            }
        } catch (e: Exception) {
            Log.w("GossipEvent", "Fail to decrypt or parse Gossip", e)
        }
    }

    private fun plainContent(
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        if (content.isEmpty()) return

        signer.nip44Decrypt(content, pubKey, onReady)
    }

    companion object {
        const val KIND = 13

        fun create(
            event: Event,
            encryptTo: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (SealedGossipEvent) -> Unit,
        ) {
            val gossip = Gossip.create(event)
            create(gossip, encryptTo, signer, createdAt, onReady)
        }

        fun create(
            gossip: Gossip,
            encryptTo: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.randomWithTwoDays(),
            onReady: (SealedGossipEvent) -> Unit,
        ) {
            val msg = Gossip.toJson(gossip)

            signer.nip44Encrypt(msg, encryptTo) { content ->
                signer.sign(createdAt, KIND, emptyArray(), content, onReady)
            }
        }
    }
}
