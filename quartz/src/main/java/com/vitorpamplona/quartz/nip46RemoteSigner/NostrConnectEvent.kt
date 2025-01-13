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
package com.vitorpamplona.quartz.nip46RemoteSigner

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.crypto.Hex
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
class NostrConnectEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(
        id,
        pubKey,
        createdAt,
        com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent.Companion.KIND,
        tags,
        content,
        sig,
    ) {
    @Transient private var decryptedContent: Map<HexKey, com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage> = mapOf()

    override fun countMemory(): Long =
        super.countMemory() +
            pointerSizeInBytes + (decryptedContent.values.sumOf { pointerSizeInBytes + it.countMemory() })

    override fun isContentEncoded() = true

    private fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun recipientPubKeyBytes() = recipientPubKey()?.runCatching { Hex.decode(this) }?.getOrNull()

    fun verifiedRecipientPubKey(): HexKey? {
        val recipient = recipientPubKey()
        return if (Hex.isHex(recipient)) {
            recipient
        } else {
            null
        }
    }

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) verifiedRecipientPubKey() ?: pubKey else pubKey

    fun plainContent(
        signer: NostrSigner,
        onReady: (com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage) -> Unit,
    ) {
        decryptedContent[signer.pubKey]?.let {
            onReady(it)
            return
        }

        // decrypts using NIP-04 or NIP-44
        signer.decrypt(content, talkingWith(signer.pubKey)) { retVal ->
            val content = EventMapper.mapper.readValue(retVal, com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage::class.java)

            decryptedContent = decryptedContent + Pair(signer.pubKey, content)

            onReady(content)
        }
    }

    companion object {
        const val KIND = 24133
        const val ALT = "Nostr Connect Event"

        fun create(
            message: com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage,
            remoteKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent) -> Unit,
        ) {
            val tags =
                arrayOf(
                    arrayOf("alt", com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent.Companion.ALT),
                    arrayOf("p", remoteKey),
                )
            signer.sign(
                createdAt,
                com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent.Companion.KIND,
                tags,
                "",
                onReady,
            )

            val encrypted = EventMapper.mapper.writeValueAsString(message)

            signer.nip44Encrypt(encrypted, remoteKey) { content ->
                signer.sign(
                    createdAt,
                    com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent.Companion.KIND,
                    tags,
                    content,
                    onReady,
                )
            }
        }
    }
}
