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
package com.vitorpamplona.quartz.buzz.aeEngrams

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Agent Engram (NIP-AE, `kind:30174`): an addressable, encrypted memory
 * record for an AI agent, authored by the agent and readable by its owner.
 *
 * Addressed by `(pubkey_a, kind, d)`, where the `d` tag is a **blinded** slug —
 * `HMAC-SHA256(K_c, "agent-memory/v1/d-tag" || 0x00 || slug)` over the agent
 * owner NIP-44 conversation key (see [EngramDTag]). The `content` is a NIP-44 v2
 * ciphertext of an [EngramBody] (a `mem/…` memory or the `core` identity).
 * Because the conversation key is symmetric, either the agent (author) or the
 * owner (`p` tag) can [decrypt]. Ground truth: `buzz-core/src/engram.rs`.
 *
 * ## d-tag limitation
 * Quartz's [NostrSigner] does not expose the NIP-44 conversation key, so the `d`
 * tag cannot be derived from the signer during [create]. The caller MUST supply
 * it as an opaque 64-hex string — compute it with [EngramDTag.derive] wherever
 * the raw conversation-key bytes are available.
 */
@Immutable
class EngramEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The owner (recipient) pubkey — the single `p` tag. */
    fun ownerPubKey() = tags.engramOwner()

    /**
     * Decrypts and parses the engram body. [signer] must belong to the agent
     * (this event's author) or the owner (`p` tag). Throws on a missing
     * counterparty, decryption failure, or malformed body; use [decryptOrNull]
     * to swallow those.
     */
    suspend fun decrypt(signer: NostrSigner): EngramBody {
        val counterparty = if (signer.pubKey == pubKey) ownerPubKey() else pubKey
        requireNotNull(counterparty) { "Engram is missing the owner (p) tag" }
        val json = signer.decrypt(content, counterparty)
        return EngramBody.decodeFromJson(json)
    }

    suspend fun decryptOrNull(signer: NostrSigner): EngramBody? =
        try {
            decrypt(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 30174

        /**
         * Builds and signs an engram: [signer] is the agent; the [body] is
         * NIP-44-encrypted to [ownerPubKey]. [dTag] is the blinded slug address —
         * an opaque 64-hex string the caller derives via [EngramDTag.derive] (the
         * signer cannot supply the conversation key needed to compute it).
         */
        suspend fun create(
            body: EngramBody,
            ownerPubKey: HexKey,
            dTag: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): EngramEvent {
            val ciphertext = signer.nip44Encrypt(body.encodeToJson(), ownerPubKey)
            return signer.sign(build(ciphertext, ownerPubKey, dTag, createdAt))
        }

        fun build(
            ciphertext: String,
            ownerPubKey: HexKey,
            dTag: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EngramEvent>.() -> Unit = {},
        ) = eventTemplate<EngramEvent>(KIND, ciphertext, createdAt) {
            dTag(dTag)
            owner(ownerPubKey)
            initializer()
        }
    }
}
