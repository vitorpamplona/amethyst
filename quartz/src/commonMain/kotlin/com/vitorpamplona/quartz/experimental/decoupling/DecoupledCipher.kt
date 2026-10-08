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
package com.vitorpamplona.quartz.experimental.decoupling

import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.setup.tags.KeyTag
import com.vitorpamplona.quartz.experimental.decoupling.store.EncryptionKeyStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip44Encryption.Nip44

/**
 * NIP-44 encryption between users who may have decoupled encryption from identity (NIP-4E, kind
 * 10044).
 *
 * A user's encryption key is the first `n` of their 10044 ([EncryptionKeyListEvent.encryptionKey]);
 * a user without one encrypts and decrypts with the identity key, through the signer. The secret of
 * one of *our* 10044 keys comes from one of two places:
 * - the [keyStore], for the draft form `["n", key]` — a random key this device generated or
 *   received through a 4454/4455 transfer;
 * - derivation by the signer (`NostrSigner.deriveKey`), for Quartz's legacy `["n", key, nonce]`
 *   form. Remote signers cannot derive, so for them that form simply yields no key.
 *
 * Without a [keyStore] only the legacy form and the identity key work.
 */
class DecoupledCipher(
    private val keyStore: EncryptionKeyStore? = null,
) {
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

    /**
     * The secret of [key], one of [signer]'s own 10044 keys, or null when this device does not hold
     * it (a draft key never transferred here, or a legacy key behind a signer that cannot derive).
     */
    suspend fun secretFor(
        key: KeyTag,
        signer: NostrSigner,
    ): ByteArray? {
        val nonce = key.nonce ?: return keyStore?.get(signer.pubKey, key.pubkey)
        return EncryptionKeyCache.getOrLoad(
            deriveFromPubKey = signer.pubKey,
            nonce = nonce,
            load = {
                try {
                    signer.deriveKey(nonce).hexToByteArray()
                } catch (e: SignerExceptions.UnsupportedMethodException) {
                    null
                }
            },
        )
    }

    /**
     * Encrypts [decryptedContent] from [signer] to the owner of [toKeyList]: to their 10044 key when
     * they announce one, else to their identity. Sends from our own 10044 key when [fromKeyList]
     * announces one — and then returns null if this device does not hold its secret, because a
     * receiver will decrypt against that announced key, so falling back to the identity key would
     * produce a message nobody can open.
     */
    suspend fun encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        fromKeyList: EncryptionKeyListEvent,
        toKeyList: EncryptionKeyListEvent,
        signer: NostrSigner,
    ): String? {
        val sendToKey = toKeyList.encryptionKey()?.pubkey ?: toKeyList.pubKey

        val ourKey = fromKeyList.encryptionKey() ?: return signer.nip44Encrypt(decryptedContent, sendToKey)

        val secret = secretFor(ourKey, signer) ?: return null
        return innerEncrypt(decryptedContent, secret, sendToKey)
    }

    /**
     * Decrypts [encryptedContent] sent by the owner of [fromKeyList] (from their 10044 key, or their
     * identity) to [toPublicKey]: our identity, which the signer handles, or one of our 10044 keys
     * listed in [toEncryptedKeyList] whose secret this device holds. Null when it holds none.
     */
    suspend fun decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        toPublicKey: HexKey,
        fromKeyList: EncryptionKeyListEvent,
        toEncryptedKeyList: EncryptionKeyListEvent,
        signer: NostrSigner,
    ): String? {
        val sentFromKey = fromKeyList.encryptionKey()?.pubkey ?: fromKeyList.pubKey

        if (signer.pubKey == toPublicKey) {
            return signer.nip44Decrypt(encryptedContent, sentFromKey)
        }

        val keyToUse = toEncryptedKeyList.keys().firstOrNull { it.pubkey == toPublicKey } ?: return null
        val secret = secretFor(keyToUse, signer) ?: return null
        return innerDecrypt(encryptedContent, secret, sentFromKey)
    }
}
