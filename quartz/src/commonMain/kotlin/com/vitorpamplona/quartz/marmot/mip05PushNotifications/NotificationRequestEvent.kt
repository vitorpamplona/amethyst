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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot push notification trigger — kind 446
 * (`features/push-notifications.md`, "Notification trigger").
 *
 * The rumor inside a gift wrap addressed to a notification server's inbox. It
 * is NOT an inner group payload: kinds 447-449 travel inside group messages,
 * this one leaves the group entirely, so the Nostr binding owns its seal, wrap
 * and publish targets.
 *
 * `pubkey` MUST be a fresh ephemeral key. That is also why a server cannot
 * deduplicate on the outer event id — a replayer re-wraps freely — and must key
 * on the content hash instead.
 *
 * The only tag is `v`. The earlier exploratory shape also required an
 * `["encoding", "base64"]` tag; the adopted rumor does not carry one, because
 * the transport's byte-encoding rule already fixes standard padded base64.
 */
@Immutable
class NotificationRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Base64 of 1 to 32 concatenated 1084-byte chunks, each an `EncryptedToken`
     * or random padding.
     */
    fun tokensBase64() = content

    /** Must be [PushGossip.VERSION]; anything else is not this protocol. */
    fun version() = tags.notificationVersion()

    /**
     * The chunks, or null when the trigger is structurally malformed.
     *
     * The length check happens before any ECDH or AEAD work, which is the point:
     * a server must be able to discard an oversized trigger without doing the
     * expensive part.
     */
    fun chunks(): List<ByteArray>? {
        val decoded = PushBase64.decodeOrNull(content) ?: return null
        if (decoded.isEmpty()) return null
        if (decoded.size % PushSignedRecord.ENCRYPTED_TOKEN_BYTES != 0) return null
        val count = decoded.size / PushSignedRecord.ENCRYPTED_TOKEN_BYTES
        if (count > MAX_CHUNKS) return null
        return List(count) {
            decoded.copyOfRange(it * PushSignedRecord.ENCRYPTED_TOKEN_BYTES, (it + 1) * PushSignedRecord.ENCRYPTED_TOKEN_BYTES)
        }
    }

    override fun isContentEncoded() = true

    companion object {
        const val KIND = 446

        /** Includes padding: padding cannot create unbounded server work. */
        const val MAX_CHUNKS = 32

        fun build(
            chunks: List<ByteArray>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NotificationRequestEvent>.() -> Unit = {},
        ): com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<NotificationRequestEvent> {
            require(chunks.isNotEmpty() && chunks.size <= MAX_CHUNKS) {
                "a push trigger carries 1..$MAX_CHUNKS chunks, got ${chunks.size}"
            }
            require(chunks.all { it.size == PushSignedRecord.ENCRYPTED_TOKEN_BYTES }) {
                "every push trigger chunk is exactly ${PushSignedRecord.ENCRYPTED_TOKEN_BYTES} bytes"
            }
            val joined = ByteArray(chunks.size * PushSignedRecord.ENCRYPTED_TOKEN_BYTES)
            chunks.forEachIndexed { index, chunk -> chunk.copyInto(joined, index * PushSignedRecord.ENCRYPTED_TOKEN_BYTES) }
            return eventTemplate(KIND, PushBase64.encode(joined), createdAt) {
                addUnique(VersionTag.assemble())
                initializer()
            }
        }
    }
}
