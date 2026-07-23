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
package com.vitorpamplona.quartz.buzz.erReminders

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Event Reminder (NIP-ER, `kind:30300`): an encrypted, author-only, addressable
 * reminder keyed by `(pubkey, 30300, d)`.
 *
 * The public [notBefore] tag tells supporting relays when the reminder is due; the
 * reminder [target], `note`, and `status` are NIP-44 encrypted to the author's own
 * public key (the same self-encryption pattern as NIP-51 private lists), so [decrypt]
 * needs the author's signer. A reminder without `not_before` is a bookmark or a terminal
 * (done/cancelled) state. Ground truth: `buzz-core/src/kind.rs` (`KIND_EVENT_REMINDER`)
 * and `buzz-relay/src/handlers/ingest.rs` (`validate_event_reminder`).
 */
@Immutable
class EventReminderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The public due-time hint (unix seconds), or null for bookmarks/terminal states. */
    fun notBefore() = tags.reminderNotBefore()

    /** NIP-40 cleanup expiration (unix seconds), when set. */
    fun expiration() = tags.expiration()

    /**
     * Decrypts and parses the reminder payload. [signer] MUST be the author (this event's
     * own key), because the content is self-encrypted. Throws on a decryption failure or a
     * malformed payload; use [decryptOrNull] to swallow those.
     */
    suspend fun decrypt(signer: NostrSigner): EventReminderPayload {
        val json = signer.decrypt(content, pubKey)
        return EventReminderPayload.decodeFromJson(json)
    }

    suspend fun decryptOrNull(signer: NostrSigner): EventReminderPayload? =
        try {
            decrypt(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 30300
        const val ALT_DESCRIPTION = "Encrypted reminder"

        /**
         * Builds and signs a reminder: [signer] is the author; [payload] is NIP-44
         * self-encrypted to the author's own key. [identifier] is the addressable `d`
         * tag and MUST be an opaque random value (>= 128 bits of entropy).
         */
        suspend fun create(
            payload: EventReminderPayload,
            identifier: HexKey,
            signer: NostrSigner,
            notBefore: Long? = null,
            expiration: Long? = null,
            createdAt: Long = TimeUtils.now(),
        ): EventReminderEvent {
            val ciphertext = signer.nip44Encrypt(payload.encodeToJson(), signer.pubKey)
            return signer.sign(build(ciphertext, identifier, notBefore, expiration, createdAt))
        }

        fun build(
            ciphertext: String,
            identifier: String,
            notBefore: Long? = null,
            expiration: Long? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EventReminderEvent>.() -> Unit = {},
        ) = eventTemplate<EventReminderEvent>(KIND, ciphertext, createdAt) {
            dTag(identifier)
            notBefore?.let { notBefore(it) }
            expiration?.let { expiration(it) }
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
