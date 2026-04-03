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
package com.vitorpamplona.quartz.marmot.mip03GroupMessages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot Group Event (MIP-03) — kind 445.
 *
 * Carries both control messages (Proposals, Commits) and application messages
 * (chat, reactions) for encrypted group communication.
 *
 * Content: base64(nonce || ciphertext) where ciphertext is ChaCha20-Poly1305
 * encrypted MLSMessage using key derived from MLS-Exporter("marmot", "group-event", 32).
 *
 * Privacy properties:
 * - pubkey is an ephemeral key (different for each event, MUST NOT be reused)
 * - "h" tag contains nostr_group_id for relay routing (not the private MLS group ID)
 * - Content is double-encrypted: MLS framing + ChaCha20-Poly1305 outer layer
 *
 * Inner application messages are unsigned Nostr events:
 * - kind:9 for chat messages
 * - kind:7 for reactions
 * - Other appropriate Nostr event kinds
 *
 * Commit ordering for race conditions:
 * 1. Lowest created_at wins
 * 2. If tied, lexicographically smallest event id wins
 * 3. All other competing Commits are discarded
 */
@Immutable
class GroupEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Base64-encoded encrypted content: nonce(12 bytes) || ciphertext.
     * Decrypt with ChaCha20-Poly1305 using the MLS exporter-derived key.
     */
    fun encryptedContent() = content

    /** Nostr group ID from the Marmot Group Data Extension (for relay routing) */
    fun groupId() = tags.marmotGroupId()

    override fun isContentEncoded() = true

    companion object {
        const val KIND = 445
        const val ALT_DESCRIPTION = "Encrypted group message"

        /** MLS Exporter label for deriving the group event encryption key */
        const val EXPORTER_LABEL = "marmot"

        /** MLS Exporter context for deriving the group event encryption key */
        const val EXPORTER_CONTEXT = "group-event"

        /** Length of the MLS exporter-derived key in bytes */
        const val EXPORTER_KEY_LENGTH = 32

        /** ChaCha20-Poly1305 nonce length in bytes */
        const val NONCE_LENGTH = 12

        /** ChaCha20-Poly1305 auth tag length in bytes */
        const val AUTH_TAG_LENGTH = 16

        /** Minimum base64-decoded content length: nonce + auth tag */
        const val MIN_CONTENT_LENGTH = NONCE_LENGTH + AUTH_TAG_LENGTH

        fun build(
            encryptedContentBase64: String,
            nostrGroupId: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, encryptedContentBase64, createdAt) {
            marmotGroupId(nostrGroupId)
            initializer()
        }
    }
}
