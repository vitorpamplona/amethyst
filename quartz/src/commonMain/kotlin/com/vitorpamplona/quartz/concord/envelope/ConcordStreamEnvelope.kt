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
package com.vitorpamplona.quartz.concord.envelope

import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The Concord stream envelope (CORD-01): a three-layer wrap → seal → rumor that
 * carries every plane's traffic on Nostr.
 *
 * This is a deliberate **inversion** of NIP-59: the outer wrap is signed by the
 * shared *stream key* (a plane's [GroupKey]) and carries an ephemeral `["p", …]`
 * tag, rather than being signed by a random key and addressed to a fixed
 * recipient. Because the true author's rumor is only ever visible after
 * decrypting under the stream conversation key, a relay can never retain or
 * display the plaintext as a public event.
 *
 * ```
 * kind 1059/21059 wrap          signed by stream key, content = NIP-44(seal, streamConvKey)
 *   └─ kind 20013/20014 seal    signed by the real author
 *        └─ rumor               unsigned author event (kind 9, 3308, 3306, …)
 * ```
 *
 * Two seal flavors (CORD-01 §Encryption):
 *  - **Plaintext seal (20014)** — `content` is the rumor JSON verbatim. Required
 *    by the Control Plane so an author's signature survives re-encryption across
 *    epochs (the exact bytes must be preserved).
 *  - **Encrypted seal (20013)** — `content` is the rumor JSON NIP-44-encrypted
 *    under the same stream conversation key, hiding it twice over. Used by every
 *    plane that never crosses an epoch or re-seeds with fresh attestations.
 *
 * All of this is pinned to the Concord v2 reference client for wire interop.
 */
object ConcordStreamEnvelope {
    const val KIND_WRAP = 1059
    const val KIND_WRAP_EPHEMERAL = 21059
    const val KIND_SEAL_ENCRYPTED = 20013
    const val KIND_SEAL_PLAINTEXT = 20014

    /**
     * Seals [rumor] for the [stream] plane, signed by [authorSigner] (the real
     * author's key). [encrypted] selects a 20013 encrypted seal; otherwise a
     * 20014 plaintext seal. The seal inherits the rumor's `created_at`.
     */
    suspend fun seal(
        rumor: Event,
        stream: GroupKey,
        authorSigner: NostrSigner,
        encrypted: Boolean,
    ): Event {
        val content =
            if (encrypted) {
                Nip44.v2.encrypt(rumor.toJson(), stream.conversationKey).encodePayload()
            } else {
                rumor.toJson()
            }
        val kind = if (encrypted) KIND_SEAL_ENCRYPTED else KIND_SEAL_PLAINTEXT
        return authorSigner.sign(rumor.createdAt, kind, EMPTY_TAGS, content)
    }

    /**
     * Wraps an already-built [seal] into a stream event at the [stream] plane's
     * address, signed by the stream key and encrypted under its conversation key.
     * Adds a fresh ephemeral `["p", …]` tag. Use [KIND_WRAP_EPHEMERAL] via
     * [ephemeral] for transient traffic (typing, voice presence).
     */
    fun wrapSeal(
        seal: Event,
        stream: GroupKey,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
    ): Event {
        val streamSigner = NostrSignerSync(KeyPair(privKey = stream.secretKey))
        val content = Nip44.v2.encrypt(seal.toJson(), stream.conversationKey).encodePayload()
        val ephemeralP = KeyPair().pubKey.toHexKey()
        val kind = if (ephemeral) KIND_WRAP_EPHEMERAL else KIND_WRAP
        return streamSigner.signNormal(createdAt, kind, arrayOf(arrayOf("p", ephemeralP)), content)
    }

    /** Convenience: [seal] then [wrapSeal] in one call. */
    suspend fun wrap(
        rumor: Event,
        stream: GroupKey,
        authorSigner: NostrSigner,
        encrypted: Boolean,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
    ): Event = wrapSeal(seal(rumor, stream, authorSigner, encrypted), stream, ephemeral, createdAt)

    /**
     * Opens a stream [wrap] for the [stream] plane and returns the verified author
     * rumor, or throws if any layer fails to validate:
     *  1. `wrap.pubkey` must equal the stream address, and the wrap must be signed
     *     by the stream key.
     *  2. `wrap.content` decrypts under the stream conversation key into a seal
     *     whose own signature must verify against `seal.pubkey`.
     *  3. For a 20013 seal the rumor decrypts under the same conversation key; a
     *     20014 seal carries it verbatim.
     *  4. The rumor's author must equal the seal's author (no impersonation) and
     *     its `id` must be the correct NIP-01 event hash.
     */
    fun open(
        wrap: Event,
        stream: GroupKey,
    ): OpenedStreamEvent {
        require(wrap.kind == KIND_WRAP || wrap.kind == KIND_WRAP_EPHEMERAL) {
            "Not a Concord stream wrap: kind ${wrap.kind}"
        }
        require(wrap.pubKey == stream.publicKeyHex) {
            "Wrap author ${wrap.pubKey} is not the stream address ${stream.publicKeyHex}"
        }
        require(wrap.verify()) { "Wrap signature/id is invalid" }

        val seal = Event.fromJson(Nip44.v2.decrypt(wrap.content, stream.conversationKey))
        require(seal.kind == KIND_SEAL_ENCRYPTED || seal.kind == KIND_SEAL_PLAINTEXT) {
            "Not a Concord seal: kind ${seal.kind}"
        }
        require(seal.verify()) { "Seal signature/id is invalid" }

        val rumorJson =
            if (seal.kind == KIND_SEAL_ENCRYPTED) {
                Nip44.v2.decrypt(seal.content, stream.conversationKey)
            } else {
                seal.content
            }

        val rumor = Event.fromJson(rumorJson)
        require(rumor.pubKey == seal.pubKey) {
            "Rumor author ${rumor.pubKey} does not match seal author ${seal.pubKey}"
        }
        require(rumor.verifyId()) { "Rumor id ${rumor.id} is not its NIP-01 hash" }

        return OpenedStreamEvent(rumor, seal.kind, seal.pubKey)
    }

    /** Like [open] but returns null instead of throwing on any validation failure. */
    fun openOrNull(
        wrap: Event,
        stream: GroupKey,
    ): OpenedStreamEvent? =
        try {
            open(wrap, stream)
        } catch (_: Exception) {
            null
        }

    private val EMPTY_TAGS = emptyArray<Array<String>>()
}

/**
 * The verified result of opening a stream wrap: the author [rumor], the
 * [sealKind] it arrived under (20013/20014), and the true [author] pubkey (equal
 * to `rumor.pubKey`, surfaced for convenience).
 */
class OpenedStreamEvent(
    val rumor: Event,
    val sealKind: Int,
    val author: String,
)
