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
package com.vitorpamplona.cordn.spec02Envelopes

import com.vitorpamplona.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.cordn.spec03Payloads.SealedPayload
import com.vitorpamplona.quartz.mls.group.DecryptedMessage
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Sending and receiving a cordn application message: envelope, MLS, seal.
 *
 * ## The authenticated-sender binding
 *
 * `spec/02.md` §5 says the envelope's `pubkey` must equal "the authenticated
 * sender identity derived from the sender's MLS credential" and stops there.
 * The reference implementation does something more specific that the spec never
 * mentions: it puts the account pubkey in MLS **`authenticated_data`**
 * (`packages/cli/src/session.ts:1000`) and, on receive, **rejects any
 * application message whose AAD is empty** before even looking at the envelope
 * (`packages/cli/src/groupSync.ts:247`).
 *
 * So a message without it is not merely unattributed, it is refused. That makes
 * the AAD a wire requirement rather than an optimisation, and it is the reason
 * this class exists instead of callers reaching for `MlsGroup.encrypt` directly.
 *
 * Using the AAD rather than the leaf credential also has a reason: the
 * credential names whichever key holds the leaf, and a linked device's leaf is
 * not the account. The AAD says who is *speaking*.
 *
 * `authenticated_data` is authenticated but **not encrypted**, so this binding
 * costs metadata: any party holding the ciphertext can read the sender's
 * account pubkey. The coordinator holds every ciphertext. cordn's reference
 * client accepts that trade; it is worth knowing it was made.
 */
object CordnApplicationMessage {
    /**
     * Builds, frames and seals an application message.
     *
     * @return the base64 sealed payload to hand to `msg_post`.
     */
    fun seal(
        group: MlsGroup,
        senderPubKey: HexKey,
        envelope: CordnEnvelope,
    ): String {
        require(envelope.pubKey == senderPubKey) {
            "envelope pubkey ${envelope.pubKey} does not match the sender $senderPubKey"
        }
        val mlsMessage = group.encrypt(envelope.encode(), authenticatedData = senderPubKey.encodeToByteArray())
        return SealedPayload.seal(mlsMessage, SealedPayload.applicationKey(group))
    }

    /**
     * Opens a sealed application message and returns its envelope.
     *
     * Every check `spec/02.md` §5 and the reference client apply, in the order
     * that makes each one meaningful: open the seal, let MLS authenticate the
     * sender, read the sender from the AAD, then hold the envelope to it.
     *
     * @throws IllegalArgumentException naming the check that failed.
     */
    fun open(
        group: MlsGroup,
        sealedBase64: String,
    ): ReceivedMessage {
        val mlsMessage = SealedPayload.open(sealedBase64, SealedPayload.applicationKey(group))
        val decrypted = group.decrypt(mlsMessage)
        return open(decrypted)
    }

    /** As [open], for a message some other path has already decrypted. */
    fun open(decrypted: DecryptedMessage): ReceivedMessage {
        // Empty is a rejection, not a default. Treating it as "unknown sender"
        // would let anyone drop the field and post as nobody in particular,
        // which the envelope's own `pubkey` would then be free to fill in.
        require(decrypted.authenticatedData.isNotEmpty()) {
            "cordn application message carries no authenticated sender"
        }
        val sender =
            try {
                decrypted.authenticatedData.decodeToString(throwOnInvalidSequence = true)
            } catch (e: CharacterCodingException) {
                throw IllegalArgumentException("cordn authenticated sender is not valid UTF-8", e)
            }

        return ReceivedMessage(
            sender = sender,
            senderLeafIndex = decrypted.senderLeafIndex,
            epoch = decrypted.epoch,
            // Holds the envelope to the MLS-authenticated sender, which is the
            // only thing making an unsigned envelope trustworthy at all.
            envelope = CordnEnvelope.decode(decrypted.content, senderIdentity = sender),
        )
    }

    /** The exporter binding both directions use, for callers that need the key itself. */
    val exporter get() = CordnGroupPolicy.PAYLOAD_EXPORTER
}

/** An application message that passed every authentication check. */
data class ReceivedMessage(
    /** The account pubkey MLS authenticated, from `authenticated_data`. */
    val sender: HexKey,
    /** Which leaf sent it. Not the same as [sender] for a linked device. */
    val senderLeafIndex: Int,
    val epoch: Long,
    val envelope: CordnEnvelope,
)
