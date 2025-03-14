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
package com.vitorpamplona.quartz.experimental.decoupling

import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip44Encryption.Nip44

class DecoupledCipher {
    fun innerEncrypt(
        content: String,
        privKey: ByteArray,
        toPublicKey: HexKey,
    ) = Nip44
        .encrypt(content, privKey, toPublicKey.hexToByteArray())
        .encodePayload()

    fun innerDecrypt(
        ciphertext: String,
        privKey: ByteArray,
        fromPublicKey: HexKey,
    ) = Nip44.decrypt(
        payload = ciphertext,
        privateKey = privKey,
        pubKey = fromPublicKey.hexToByteArray(),
    )

    fun encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        fromKeyList: EncryptionKeyListEvent,
        toKeyList: EncryptionKeyListEvent,
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        val toKeys = toKeyList.keys()
        val sendToKey = if (toKeys.isEmpty()) toKeyList.pubKey else toKeys.random().pubkey

        val fromKeys = fromKeyList.keys()

        // uses the main key
        if (fromKeys.isEmpty()) {
            signer.nip44Encrypt(decryptedContent, sendToKey, onReady)
        } else {
            val keyToUse = fromKeys.random()

            EncryptionKeyCache.getOrLoad(
                deriveFromPubKey = signer.pubKey,
                nonce = keyToUse.nonce,
                load = { onLoaded ->
                    signer.deriveKey(keyToUse.nonce) { newPrivKey ->
                        onLoaded(newPrivKey.hexToByteArray())
                    }
                },
            ) { derivedPrivKey ->
                onReady(innerEncrypt(decryptedContent, derivedPrivKey, sendToKey))
            }
        }
    }

    fun decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        toPublicKey: HexKey,
        fromKeyList: EncryptionKeyListEvent,
        toEncryptedKeyList: EncryptionKeyListEvent,
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        val fromKeys = fromKeyList.keys()
        val sentFromKey = if (fromKeys.isEmpty()) fromKeyList.pubKey else fromKeys.random().pubkey

        val keyToUse = toEncryptedKeyList.keys().firstOrNull { it.pubkey == toPublicKey }

        // uses the main key
        if (signer.pubKey == toPublicKey) {
            signer.nip44Decrypt(encryptedContent, sentFromKey, onReady)
        } else if (keyToUse != null) {
            EncryptionKeyCache.getOrLoad(
                deriveFromPubKey = signer.pubKey,
                nonce = keyToUse.nonce,
                load = { onLoaded ->
                    signer.deriveKey(keyToUse.nonce) { newPrivKey ->
                        onLoaded(newPrivKey.hexToByteArray())
                    }
                },
            ) { derivedPrivKey ->
                innerDecrypt(encryptedContent, derivedPrivKey, sentFromKey)?.let { onReady(it) }
            }
        }
    }
}
