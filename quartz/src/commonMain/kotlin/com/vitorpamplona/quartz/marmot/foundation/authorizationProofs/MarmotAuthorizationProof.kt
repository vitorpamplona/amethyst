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
package com.vitorpamplona.quartz.marmot.foundation.authorizationProofs

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto

/**
 * The common Marmot authorization-proof envelope (spec
 * `foundation/authorization-proofs.md`).
 *
 * A Nostr account key authorizes protocol bytes by signing an ordinary Nostr
 * event, and only this 104-byte summary of that event travels in the carrier:
 *
 * ```text
 * struct {
 *   opaque signer_pubkey[32];
 *   uint64 created_at;
 *   opaque signature[64];
 * } MarmotAuthorizationProof;
 * ```
 *
 * The indirection through an event exists so an external signer — NIP-46
 * bunker, NIP-55 app — can produce a proof without exposing raw BIP-340
 * signing. Every field is fixed width, so there are no length prefixes and no
 * version field: the carrier's component id *is* the format version.
 *
 * The envelope carries neither the event id nor a copy of the event. A
 * verifier rebuilds both from these bytes plus the owning proof class's
 * kind/tags/content, which is what binds the signature to a specific
 * authority class rather than to "some event this key once signed".
 *
 * Note what deliberately is NOT here: any comparison of [createdAt] against a
 * local clock. A proof does not expire because its timestamp is old. Two
 * members validating the same Commit must reach the same answer, and a
 * wall-clock rule would let skew fork them.
 */
class MarmotAuthorizationProof(
    /** Raw 32-byte x-only secp256k1 account key that signed the proof event. */
    val signerPubKey: ByteArray,
    /** Unsigned Unix seconds, `1..MAX_CREATED_AT`. Signed as part of the event. */
    val createdAt: Long,
    /** 64-byte BIP-340 signature over the proof event's 32-byte id. */
    val signature: ByteArray,
) {
    init {
        require(signerPubKey.size == PUBKEY_SIZE) {
            "MarmotAuthorizationProof.signer_pubkey must be $PUBKEY_SIZE bytes, was ${signerPubKey.size}"
        }
        require(signature.size == SIGNATURE_SIZE) {
            "MarmotAuthorizationProof.signature must be $SIGNATURE_SIZE bytes, was ${signature.size}"
        }
        require(createdAt in 1..MAX_CREATED_AT) {
            "MarmotAuthorizationProof.created_at must be in 1..$MAX_CREATED_AT, was $createdAt"
        }
    }

    val signerPubKeyHex: HexKey get() = signerPubKey.toHexKey()

    fun encode(): ByteArray {
        val writer = TlsWriter()
        writer.putBytes(signerPubKey)
        writer.putUint64(createdAt)
        writer.putBytes(signature)
        return writer.toByteArray()
    }

    /**
     * Rebuild the proof event's id from this envelope's signer/timestamp plus
     * the owning proof class's [kind], [tags] and [content], then check
     * [signature] against it.
     *
     * The caller supplies the class-specific half; getting a tag order, arity
     * or value wrong yields a different id and therefore a failed check, which
     * is exactly the binding the spec wants.
     */
    fun verifySignatureOver(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Boolean {
        val eventId = EventHasher.hashIdBytes(signerPubKeyHex, createdAt, kind, tags, content)
        return Nip01Crypto.verify(signature, eventId, signerPubKey)
    }

    companion object {
        const val PUBKEY_SIZE = 32
        const val SIGNATURE_SIZE = 64

        /** Exactly 32 + 8 + 64. A carrier entry of any other length is malformed. */
        const val SIZE = PUBKEY_SIZE + 8 + SIGNATURE_SIZE

        /**
         * `2^53 - 1`. The spec caps the timestamp here so every JSON
         * implementation that touches the signing event represents it exactly —
         * a value that survives a round trip through a double is a value two
         * clients agree on.
         */
        const val MAX_CREATED_AT = 9007199254740991L

        /**
         * Decode exactly [SIZE] bytes. Truncation and trailing bytes are both
         * rejected: the carrier hands us a component's whole data field, and a
         * dictionary entry that is not exactly one proof is malformed, not a
         * proof with something appended.
         */
        fun decode(bytes: ByteArray): MarmotAuthorizationProof {
            require(bytes.size == SIZE) {
                "MarmotAuthorizationProof must be exactly $SIZE bytes, was ${bytes.size}"
            }
            val reader = TlsReader(bytes)
            return MarmotAuthorizationProof(
                signerPubKey = reader.readBytes(PUBKEY_SIZE),
                createdAt = reader.readUint64(),
                signature = reader.readBytes(SIGNATURE_SIZE),
            )
        }

        /** [decode] without the throw, for validators that report a reason instead. */
        fun decodeOrNull(bytes: ByteArray): MarmotAuthorizationProof? =
            try {
                decode(bytes)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }
    }
}
