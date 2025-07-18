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
package com.vitorpamplona.quartz.nip01Core.signers

import com.vitorpamplona.quartz.experimental.decoupling.EncryptionKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventAssembler
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder

class NostrSignerSync(
    val keyPair: KeyPair = KeyPair(),
    val pubKey: HexKey = keyPair.pubKey.toHexKey(),
) {
    fun <T : Event> sign(ev: EventTemplate<T>) = signNormal<T>(ev.createdAt, ev.kind, ev.tags, ev.content)

    fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        return if (isUnsignedPrivateZapEvent(kind, tags)) {
            // this is a private zap
            PrivateZapRequestBuilder().signPrivateZapRequest(createdAt, kind, tags, content, this)
        } else {
            signNormal(createdAt, kind, tags, content)
        }
    }

    fun isUnsignedPrivateZapEvent(
        kind: Int,
        tags: Array<Array<String>>,
    ): Boolean =
        kind == LnZapRequestEvent.KIND &&
            tags.any { t -> t.size > 1 && t[0] == "anon" && t[1].isBlank() }

    fun <T : Event> signNormal(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        return EventAssembler.hashAndSign<T>(pubKey, createdAt, kind, tags, content, keyPair.privKey)
    }

    fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        return Nip04.encrypt(
            plaintext,
            keyPair.privKey,
            toPublicKey.hexToByteArray(),
        )
    }

    fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()
        if (ciphertext.isBlank()) throw SignerExceptions.NothingToDecrypt()

        return Nip04.decrypt(ciphertext, keyPair.privKey, fromPublicKey.hexToByteArray())
    }

    fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        return Nip44
            .encrypt(
                plaintext,
                keyPair.privKey,
                toPublicKey.hexToByteArray(),
            ).encodePayload()
    }

    fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()
        if (ciphertext.isBlank()) throw SignerExceptions.NothingToDecrypt()

        return Nip44.decrypt(
            payload = ciphertext,
            privateKey = keyPair.privKey,
            pubKey = fromPublicKey.hexToByteArray(),
        )
    }

    fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = PrivateZapRequestBuilder().decryptZapEvent(event, this)

    fun deriveKey(nonce: HexKey): HexKey {
        if (keyPair.privKey == null) throw SignerExceptions.ReadOnlyException()

        return EncryptionKeyDerivation.derivePrivateKey(keyPair.privKey, nonce.hexToByteArray()).toHexKey()
    }

    suspend fun decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
    ): String =
        if (EncryptedInfo.isNIP04(encryptedContent)) {
            nip04Decrypt(encryptedContent, fromPublicKey)
        } else {
            nip44Decrypt(encryptedContent, fromPublicKey)
        }
}
