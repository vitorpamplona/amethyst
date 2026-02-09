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
package com.vitorpamplona.quartz.nip46RemoteSigner

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NostrConnectEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun canDecrypt(signer: NostrSigner) = pubKey == signer.pubKey || recipientPubKey() == signer.pubKey

    suspend fun decryptMessage(signer: NostrSigner): BunkerMessage {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()

        val retVal = signer.decrypt(content, talkingWith(signer.pubKey))
        return OptimizedJsonMapper.fromJsonTo<BunkerMessage>(retVal)
    }

    private fun recipientPubKey() = tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1)

    fun verifiedRecipientPubKey(): HexKey? {
        val recipient = recipientPubKey()
        return if (Hex.isHex(recipient)) {
            recipient
        } else {
            null
        }
    }

    fun talkingWith(oneSideHex: String): HexKey = if (pubKey == oneSideHex) verifiedRecipientPubKey() ?: pubKey else pubKey

    companion object {
        const val KIND = 24133
        const val ALT = "Nostr Connect Event"

        suspend fun create(
            message: BunkerMessage,
            remoteKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): NostrConnectEvent =
            signer.sign(
                createdAt = createdAt,
                kind = KIND,
                tags =
                    arrayOf(
                        arrayOf("p", remoteKey),
                    ),
                content =
                    signer.nip44Encrypt(
                        plaintext = OptimizedJsonMapper.toJson(message),
                        toPublicKey = remoteKey,
                    ),
            )
    }
}
