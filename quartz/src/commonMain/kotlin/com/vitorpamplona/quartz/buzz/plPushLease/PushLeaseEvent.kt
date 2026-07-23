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
package com.vitorpamplona.quartz.buzz.plPushLease

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
 * A Buzz Push Lease (NIP-PL, `kind:30350`): an encrypted, author-only, addressable event
 * whose content carries an endpoint-bearing NIP-44 [PushLeaseDescriptor], readable only by
 * its authenticated author (self-encryption).
 *
 * Public tags are the addressable [dTag] (the installation id), a NIP-40 [expiration]
 * (the lease TTL), the [exec] executor key id, and an optional `alt`. The descriptor —
 * origin, transport, endpoint, generation, active flag, and subscriptions — stays private
 * in the ciphertext. Ground truth: `buzz-core/src/kind.rs` (`KIND_PUSH_LEASE`) and
 * `buzz-relay/src/handlers/push_lease.rs` (`validate_envelope` / `LeasePlaintext`).
 */
@Immutable
class PushLeaseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The installation id — the addressable `d` tag. */
    fun installationId() = dTag()

    /** The lease expiration (unix seconds) — the NIP-40 `expiration` tag. */
    fun expiration() = tags.expiration()

    /** The executor key id — the `exec` tag. */
    fun executorKeyId() = tags.pushLeaseExec()

    /**
     * Decrypts and parses the lease descriptor. [signer] MUST be the author (this event's
     * own key), because the content is self-encrypted. Throws on a decryption failure or a
     * malformed descriptor; use [decryptOrNull] to swallow those.
     */
    suspend fun decrypt(signer: NostrSigner): PushLeaseDescriptor {
        val json = signer.decrypt(content, pubKey)
        return PushLeaseDescriptor.decodeFromJson(json)
    }

    suspend fun decryptOrNull(signer: NostrSigner): PushLeaseDescriptor? =
        try {
            decrypt(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 30350
        const val ALT_DESCRIPTION = "Encrypted push lease"

        /**
         * Builds and signs a push lease: [signer] is the author; [descriptor] is NIP-44
         * self-encrypted to the author's own key. [installationId] is the addressable `d`
         * tag, [expiration] the lease TTL (unix seconds), and [executorKeyId] the `exec`
         * gateway key id.
         */
        suspend fun create(
            descriptor: PushLeaseDescriptor,
            installationId: String,
            expiration: Long,
            executorKeyId: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): PushLeaseEvent {
            val ciphertext = signer.nip44Encrypt(descriptor.encodeToJson(), signer.pubKey)
            return signer.sign(build(ciphertext, installationId, expiration, executorKeyId, createdAt))
        }

        fun build(
            ciphertext: String,
            installationId: String,
            expiration: Long,
            executorKeyId: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PushLeaseEvent>.() -> Unit = {},
        ) = eventTemplate<PushLeaseEvent>(KIND, ciphertext, createdAt) {
            dTag(installationId)
            expiration(expiration)
            exec(executorKeyId)
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
