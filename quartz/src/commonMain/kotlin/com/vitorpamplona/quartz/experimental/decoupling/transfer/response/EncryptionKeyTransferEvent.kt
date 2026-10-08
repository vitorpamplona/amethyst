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
package com.vitorpamplona.quartz.experimental.decoupling.transfer.response

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decoupling.DecoupledCipher
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-4E (draft, nips#1647) encryption-key **transfer**, kind 4455: one device's answer to an
 * [EncryptionKeyRequestEvent], carrying the user's shared encryption private key to the requesting
 * device. Signed by the identity key.
 *
 * - `P`: the sending device's client key ([senderClientKey]).
 * - `p`: the requesting device's client key; the PsstPsst profile also tags the identity itself
 *   "for account inbox routing", so the requester is the `p` that is not the author.
 * - `content`: the encryption private key as 64 lowercase hex characters, NIP-44 encrypted with the
 *   sender client private key and the requester client public key.
 *
 * Use [build] to make one and [decryptKey] / [decryptAndVerify] to read one. A requester accepts the
 * key only when its public key equals the user's current [EncryptionKeyListEvent] key, which is what
 * [decryptAndVerify] checks. The content is ciphertext of a private key: it is never indexed.
 */
@Immutable
class EncryptionKeyTransferEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    override fun pubKeyHints(): List<PubKeyHint> = tags.recipientHints()

    /**
     * `RECIPIENT`: the requesting device's client key, which the encrypted key is addressed to, and,
     * when present, the identity's own inbox `p`. The `P` sender client key is not a reference: it is
     * the other half of the NIP-44 conversation key.
     */
    override fun linkedPubKeys(): List<HexKey> = tags.recipientKeys()

    fun senderClientKey() = tags.senderClientKey()

    /** The requesting device's client key: the first `p` that is not the author's identity. */
    fun requesterClientKey() = tags.requesterClientKey(pubKey)

    fun recipients() = tags.recipientKeys()

    /**
     * Decrypts the transferred encryption private key with the requesting device's client private
     * key. Returns null when there is no valid `P`, the ciphertext does not open with this key, or the
     * plaintext is not a 32-byte hex key.
     */
    fun decryptKey(requesterClientPrivKey: ByteArray): HexKey? {
        val sender = senderClientKey() ?: return null
        if (content.isBlank()) return null
        val plain =
            try {
                DecoupledCipher().innerDecrypt(content, requesterClientPrivKey, sender)
            } catch (e: Exception) {
                return null
            }
        val key = plain.trim().lowercase()
        if (key.length != 64 || !Hex.isHex64(key)) return null
        return key
    }

    /**
     * [decryptKey], accepted only when the decrypted key's public key equals [expectedEncryptionPubKey],
     * the `n` of the user's current kind 10044. NIP-4E: undecryptable and non-matching responses are
     * ignored.
     */
    fun decryptAndVerify(
        requesterClientPrivKey: ByteArray,
        expectedEncryptionPubKey: HexKey,
    ): HexKey? {
        val key = decryptKey(requesterClientPrivKey) ?: return null
        val derived =
            try {
                Nip01Crypto.pubKeyCreate(Hex.decode64(key)).toHexKey()
            } catch (e: Exception) {
                return null
            }
        return if (derived.equals(expectedEncryptionPubKey, ignoreCase = true)) key else null
    }

    companion object {
        const val KIND = 4455
        const val ALT_DESCRIPTION = "Encryption key transfer"

        /**
         * Encrypts [encryptionPrivKey] (64 hex characters) from [senderClientPrivKey] to
         * [requesterClientPubKey] and tags both client keys. Pass [identityPubKey] to also tag the
         * identity for inbox routing, as PsstPsst does.
         */
        fun build(
            encryptionPrivKey: HexKey,
            senderClientPrivKey: ByteArray,
            requesterClientPubKey: HexKey,
            requesterRelayHint: NormalizedRelayUrl? = null,
            identityPubKey: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EncryptionKeyTransferEvent>.() -> Unit = {},
        ): EventTemplate<EncryptionKeyTransferEvent> {
            val senderClientPubKey = Nip01Crypto.pubKeyCreate(senderClientPrivKey).toHexKey()
            val ciphertext = DecoupledCipher().innerEncrypt(encryptionPrivKey.lowercase(), senderClientPrivKey, requesterClientPubKey)
            return eventTemplate(KIND, ciphertext, createdAt) {
                senderClientKey(senderClientPubKey)
                identityPubKey?.let { recipient(it) }
                recipient(requesterClientPubKey, requesterRelayHint)
                initializer()
            }
        }
    }
}
