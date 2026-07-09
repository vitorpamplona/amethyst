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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.crypto.EditionHash
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueAsLong
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * The exact Grant edition an actor claims authority under (the `vac` tag,
 * CORD-04). Verifiers block until they have synced this Grant, then resolve the
 * actor's rank against it — a demoted member's stale citation is dropped.
 */
class AuthorityCitation(
    val grantId: ByteArray,
    val grantVersion: Long,
    val grantHash: ByteArray,
)

/**
 * A single Control Plane edition (a kind-3308 author rumor, CORD-02/04).
 *
 * Editions are versioned, chainable state: each carries an entity id ([entityId],
 * `eid`), a monotonically increasing [version] (`ev`), the [prevHash] of the
 * previous edition (`ep`, absent for the genesis edition), an optional
 * [authorityCitation] (`vac`) pinning the Grant the [author] acts under, and the
 * verbatim entity [content]. Its identity is [hash] — a domain-separated hash of
 * exactly those fields (see [EditionHash]) — which the next edition cites in `ep`,
 * forming an unforgeable chain.
 */
class ControlEdition(
    val entityKind: ControlEntityKind,
    val entityId: ByteArray,
    val version: Long,
    val prevHash: ByteArray?,
    val authorityCitation: AuthorityCitation?,
    val content: String,
    /** The actor's real pubkey (the seal/rumor author). */
    val author: String,
    /** The rumor's event id — the deterministic tie-break key at equal version. */
    val rumorId: String,
    val createdAt: Long,
) {
    /** Domain-separated edition identity; the next edition's `ep` cites this. */
    val hash: ByteArray by lazy { EditionHash.hash(entityId, version, prevHash, content) }

    val entityIdHex: String get() = entityId.toHexKey()
    val hashHex: String get() = hash.toHexKey()

    companion object {
        const val TAG_VSK = "vsk"
        const val TAG_EID = "eid"
        const val TAG_EV = "ev"
        const val TAG_EP = "ep"
        const val TAG_VAC = "vac"

        /**
         * Parses a decrypted, verified kind-3308 [rumor] (its [author] is the
         * rumor's pubkey) into a [ControlEdition], or returns null if it is not a
         * well-formed control edition (unknown/absent `vsk`, missing `eid`/`ev`,
         * malformed hex, …) so the caller drops it rather than folding garbage.
         */
        fun fromRumor(
            rumor: Event,
            author: String = rumor.pubKey,
        ): ControlEdition? {
            if (rumor.kind != ConcordKinds.CONTROL) return null
            val entityKind = rumor.tags.firstTagValue(TAG_VSK)?.let { ControlEntityKind.of(it) } ?: return null
            val entityId =
                rumor.tags
                    .firstTagValue(TAG_EID)
                    ?.hexToByteArrayOrNull()
                    ?.takeIf { it.size == 32 } ?: return null
            val version = rumor.tags.firstTagValueAsLong(TAG_EV) ?: return null
            if (version < 0) return null

            val prevValue = rumor.tags.firstTagValue(TAG_EP)
            val prevHash = if (prevValue.isNullOrBlank()) null else prevValue.hexToByteArrayOrNull()?.takeIf { it.size == 32 } ?: return null

            val vacTag = rumor.tags.firstOrNull { it.size >= 4 && it[0] == TAG_VAC }
            val vac =
                vacTag?.let {
                    val gid = it[1].hexToByteArrayOrNull()?.takeIf { b -> b.size == 32 } ?: return null
                    val gver = it[2].toLongOrNull() ?: return null
                    val ghash = it[3].hexToByteArrayOrNull()?.takeIf { b -> b.size == 32 } ?: return null
                    AuthorityCitation(gid, gver, ghash)
                }

            return ControlEdition(
                entityKind = entityKind,
                entityId = entityId,
                version = version,
                prevHash = prevHash,
                authorityCitation = vac,
                content = rumor.content,
                author = author,
                rumorId = rumor.id,
                createdAt = rumor.createdAt,
            )
        }
    }
}
