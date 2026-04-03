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
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot Notification Request Event (MIP-05) — kind 446.
 *
 * An unsigned rumor event delivered via NIP-59 gift wrap to the notification server.
 * Contains concatenated EncryptedTokens (each 280 bytes), base64-encoded.
 *
 * Flow: Rumor(kind:446) → Seal(kind:13) → GiftWrap(kind:1059) → notification server
 *
 * The pubkey MUST be a fresh ephemeral key (not the sender's identity)
 * to prevent the notification server from linking events to users.
 *
 * Content includes real group tokens plus decoy tokens from other groups
 * (shuffled) to obscure group size and prevent social graph inference.
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
     * Base64-encoded concatenation of EncryptedTokens.
     * Each token is exactly 280 bytes when decoded.
     * Total decoded length MUST be a multiple of 280.
     */
    fun tokensBase64() = content

    /** Notification protocol version (must be "mip05-v1") */
    fun version() = tags.notificationVersion()

    /** Content encoding (must be "base64") */
    fun encoding() = tags.notificationEncoding()

    override fun isContentEncoded() = true

    companion object {
        const val KIND = 446

        fun build(
            tokensBase64: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NotificationRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, tokensBase64, createdAt) {
            addUnique(VersionTag.assemble())
            addUnique(EncodingTag.assemble())
            initializer()
        }
    }
}
