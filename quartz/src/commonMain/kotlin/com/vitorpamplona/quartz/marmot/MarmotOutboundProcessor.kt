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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/**
 * Result of building an outbound GroupEvent.
 */
data class OutboundGroupEvent(
    val signedEvent: GroupEvent,
    val nostrGroupId: HexKey,
)

/**
 * Handles outbound Marmot message encryption and event construction.
 *
 * Creates GroupEvent (kind:445) from inner Nostr events by:
 * 1. MLS-encrypting the inner event via MlsGroupManager
 * 2. Wrapping with ChaCha20-Poly1305 outer layer via GroupEventEncryption
 * 3. Building a GroupEvent with an ephemeral signing key
 *
 * **Ephemeral keys:** Each outbound kind:445 MUST use a fresh random
 * keypair for signing. This is critical for sender privacy — the pubkey
 * on the GroupEvent does NOT reveal the actual sender's Nostr identity.
 */
class MarmotOutboundProcessor(
    private val groupManager: MlsGroupManager,
) {
    /**
     * Encrypt an inner Nostr event and build a GroupEvent for publishing.
     *
     * Flow:
     * 1. Serialize the inner event to JSON bytes
     * 2. MLS encrypt via MlsGroupManager.encrypt() → MLS ciphertext
     * 3. Outer ChaCha20-Poly1305 encrypt via GroupEventEncryption → base64 content
     * 4. Build GroupEvent template with the group's `h` tag
     * 5. Sign with a fresh ephemeral keypair (NOT the user's Nostr key)
     *
     * @param nostrGroupId the Nostr group ID to send to
     * @param innerEvent the inner Nostr event (e.g., kind:9 chat, kind:7 reaction)
     * @return the signed GroupEvent ready for relay publishing
     * @throws IllegalStateException if not a member of the group
     */
    suspend fun buildGroupEvent(
        nostrGroupId: HexKey,
        innerEvent: Event,
    ): OutboundGroupEvent = buildGroupEventFromBytes(nostrGroupId, innerEvent.toJson().encodeToByteArray())

    /**
     * Encrypt raw bytes and build a GroupEvent for publishing.
     *
     * This lower-level variant accepts raw bytes instead of an Event,
     * useful for sending non-event application data.
     *
     * @param nostrGroupId the Nostr group ID
     * @param plaintext the plaintext bytes to encrypt
     * @return the signed GroupEvent ready for relay publishing
     */
    suspend fun buildGroupEventFromBytes(
        nostrGroupId: HexKey,
        plaintext: ByteArray,
    ): OutboundGroupEvent {
        // Step 1: MLS encrypt
        val mlsCiphertext = groupManager.encrypt(nostrGroupId, plaintext)

        // Step 2: Outer ChaCha20-Poly1305 encryption
        val exporterKey = groupManager.exporterSecret(nostrGroupId)
        val encryptedContent = GroupEventEncryption.encrypt(mlsCiphertext, exporterKey)

        // Step 3: Build the GroupEvent template
        val template =
            GroupEvent.build(
                encryptedContentBase64 = encryptedContent,
                nostrGroupId = nostrGroupId,
            )

        // Step 4: Sign with a fresh ephemeral keypair
        val ephemeralSigner = NostrSignerInternal(KeyPair())
        val signedEvent: GroupEvent = ephemeralSigner.sign(template)

        return OutboundGroupEvent(
            signedEvent = signedEvent,
            nostrGroupId = nostrGroupId,
        )
    }

    /**
     * Build a GroupEvent carrying a Commit for publishing.
     *
     * Used after MlsGroupManager.commit() or addMember()/removeMember().
     * The commit bytes are already MLS-formatted.
     *
     * @param nostrGroupId the Nostr group ID
     * @param commitBytes the raw MLS commit bytes from CommitResult
     * @return the signed GroupEvent ready for relay publishing
     */
    suspend fun buildCommitEvent(
        nostrGroupId: HexKey,
        commitBytes: ByteArray,
    ): OutboundGroupEvent {
        // Outer ChaCha20-Poly1305 encryption of the MLS commit
        val exporterKey = groupManager.exporterSecret(nostrGroupId)
        val encryptedContent = GroupEventEncryption.encrypt(commitBytes, exporterKey)

        // Build the GroupEvent template
        val template =
            GroupEvent.build(
                encryptedContentBase64 = encryptedContent,
                nostrGroupId = nostrGroupId,
            )

        // Sign with a fresh ephemeral keypair
        val ephemeralSigner = NostrSignerInternal(KeyPair())
        val signedEvent: GroupEvent = ephemeralSigner.sign(template)

        return OutboundGroupEvent(
            signedEvent = signedEvent,
            nostrGroupId = nostrGroupId,
        )
    }
}
