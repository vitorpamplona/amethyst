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
package com.vitorpamplona.quartz.buzz.pairing

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A NIP-AB device-pairing event (`kind:24134`): one encrypted message in a QR-initiated,
 * end-to-end-encrypted device-pairing session. Ephemeral (20000–29999) — the relay may
 * discard it after delivery.
 *
 * The `content` is a NIP-44 v2 ciphertext of a [PairingMessage], encrypted between two
 * ephemeral keypairs; the single `p` tag names the recipient's ephemeral pubkey. Because the
 * NIP-44 conversation key is symmetric, either the sender or the recipient can [decrypt].
 * Ground truth: `buzz-core/src/pairing/session.rs`, `buzz-core/src/pairing/types.rs`.
 */
@Immutable
class PairingEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The recipient's ephemeral pubkey — the `p` tag. */
    fun recipientPubKey() = tags.pairingRecipient()

    /**
     * Decrypts and parses the pairing message. [signer] must own either this event's author
     * key or the recipient (`p` tag) key. Throws on a missing counterparty, decryption
     * failure, or malformed message; use [decryptOrNull] to swallow those.
     */
    suspend fun decrypt(signer: NostrSigner): PairingMessage {
        val counterparty = if (signer.pubKey == pubKey) recipientPubKey() else pubKey
        requireNotNull(counterparty) { "Pairing event is missing the recipient (p) tag" }
        val json = signer.decrypt(content, counterparty)
        return PairingMessage.decodeFromJson(json)
    }

    suspend fun decryptOrNull(signer: NostrSigner): PairingMessage? =
        try {
            decrypt(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 24134

        /**
         * Builds and signs a pairing event: [signer] is the sender; [message] is NIP-44
         * encrypted to [recipientPubKey].
         */
        suspend fun create(
            message: PairingMessage,
            recipientPubKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PairingEvent {
            val ciphertext = signer.nip44Encrypt(message.encodeToJson(), recipientPubKey)
            return signer.sign(build(ciphertext, recipientPubKey, createdAt))
        }

        fun build(
            ciphertext: String,
            recipientPubKey: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PairingEvent>.() -> Unit = {},
        ) = eventTemplate<PairingEvent>(KIND, ciphertext, createdAt) {
            recipient(recipientPubKey)
            initializer()
        }
    }
}
