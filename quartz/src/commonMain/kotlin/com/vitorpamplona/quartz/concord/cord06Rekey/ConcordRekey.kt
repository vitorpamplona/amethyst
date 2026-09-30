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
package com.vitorpamplona.quartz.concord.cord06Rekey

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import kotlinx.serialization.builtins.ListSerializer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Rekey distribution (CORD-06): non-ratcheted, asynchronous key rotation that
 * removes members from a channel (or, in a Refounding, the whole community) while
 * keeping the new key secret from those removed.
 *
 * The rotator publishes a kind-3303 rumor whose content is a JSON array of
 * [RekeyBlob]s, one per remaining member. Each blob's `locator` is the recipient's
 * pseudonym (public-input HKDF), and its `wrapped` field is the fixed-width
 * [RekeyPayload] (base64 → NIP-44 under the rotator↔recipient pairwise key) —
 * 72 bytes for a channel rotation, 104/136 for a base rotation's member/staff
 * forms carrying the next epoch's Control Plane keys (CORD-02 §2). A recipient
 * computes their own locator, finds the matching blob, and decrypts the new
 * key(s); a member with no matching blob across all chunks of a complete rotation
 * has been removed.
 *
 * Pinned to the Concord v2 reference client for interop.
 */
object ConcordRekey {
    const val TAG_SCOPE = "scope"
    const val TAG_NEWEPOCH = "newepoch"
    const val TAG_PREVEPOCH = "prevepoch"
    const val TAG_PREVCOMMIT = "prevcommit"
    const val TAG_CHUNK = "chunk"

    /** All-zero scope id marks a community_root refounding rather than a channel rekey. */
    val ROOT_SCOPE: ByteArray = ByteArray(32)

    /**
     * Builds a rekey blob delivering [newKey] to one recipient. On a base rotation
     * pass [newControlPk] (every member) and, for a staff recipient, also
     * [newControlRoot] (CORD-06 §1) — the widths select the 104/136-byte forms.
     *
     * @param rotatorPrivKey the rotator's private key (their real identity)
     * @param rotatorXOnly   the rotator's x-only pubkey
     * @param recipientXOnly the recipient's x-only pubkey
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun blobFor(
        rotatorPrivKey: ByteArray,
        rotatorXOnly: ByteArray,
        recipientXOnly: ByteArray,
        scopeId: ByteArray,
        newEpoch: Long,
        newKey: ByteArray,
        newControlPk: ByteArray? = null,
        newControlRoot: ByteArray? = null,
    ): RekeyBlob {
        val locator = ConcordKeyDerivation.recipientLocator(rotatorXOnly, recipientXOnly, scopeId, newEpoch).toHexKey()
        val payloadB64 = Base64.Default.encode(RekeyPayload(scopeId, newEpoch, newKey, newControlPk, newControlRoot).encode())
        val convKey = Nip44.v2.getConversationKey(rotatorPrivKey, recipientXOnly)
        val wrapped = Nip44.v2.encrypt(payloadB64, convKey).encodePayload()
        return RekeyBlob(locator, wrapped)
    }

    /**
     * The kind-3303 rumor tags for a rekey chunk. [chunkIndex] is **1-based** (CORD-06 §2's
     * "chunk i of n"; the reference client refuses an index below 1, so a 0-based chunk makes the
     * whole rotation unreadable to it). [authority] is the rotator's `vac` citation (CORD-06 §3
     * "Authority", CORD-04 §5), carried on EVERY chunk so a partial holder can judge authority;
     * null when the owner rotates.
     */
    fun tags(
        scopeId: ByteArray,
        newEpoch: Long,
        prevEpoch: Long,
        prevCommit: HexKey,
        chunkIndex: Int,
        chunkTotal: Int,
        authority: AuthorityCitation? = null,
    ): Array<Array<String>> {
        require(chunkTotal >= 1 && chunkIndex in 1..chunkTotal) { "chunk must be 1..n, was $chunkIndex of $chunkTotal" }
        val base =
            arrayOf(
                arrayOf(TAG_SCOPE, scopeId.toHexKey()),
                arrayOf(TAG_NEWEPOCH, newEpoch.toString()),
                arrayOf(TAG_PREVEPOCH, prevEpoch.toString()),
                arrayOf(TAG_PREVCOMMIT, prevCommit),
                arrayOf(TAG_CHUNK, chunkIndex.toString(), chunkTotal.toString()),
            )
        return if (authority == null) base else base + arrayOf(VacTag.assemble(authority))
    }

    /** Strict decimal (`0|[1-9][0-9]*`): digits only, no sign, exponent, radix prefix, padding or leading zero. */
    private fun strictDecimal(value: String?): Int? {
        if (value.isNullOrEmpty() || value.length > 9 || !value.all { it in '0'..'9' }) return null
        if (value.length > 1 && value[0] == '0') return null
        return value.toInt()
    }

    /**
     * The `["chunk", i, n]` position of a kind-3303 rumor as a 1-based `(i, n)` pair, or null when
     * the tag is malformed: non-decimal, `i < 1`, `n < 1` or `i > n` (a 0-based chunk included).
     * An absent tag reads as the single chunk `(1, 1)`, as the reference client does.
     */
    fun chunkOf(tags: Array<Array<String>>): Pair<Int, Int>? {
        val tag = tags.firstOrNull { it.isNotEmpty() && it[0] == TAG_CHUNK } ?: return 1 to 1
        val index = strictDecimal(tag.getOrNull(1)) ?: return null
        val count = strictDecimal(tag.getOrNull(2)) ?: return null
        if (index < 1 || count < 1 || index > count) return null
        return index to count
    }

    /**
     * Splits [blobs] into chunks that each fit one kind-3303 rumor: at most
     * [MAX_BLOBS_PER_CHUNK] blobs AND a rumor JSON of at most [REKEY_RUMOR_MAX_BYTES], given the
     * [envelopeBytes] the rumor costs with an empty content (measure it at the widest `chunk` tag
     * the rotation can carry — over-reserving only makes chunks smaller). Mirrors the reference
     * client's budget byte for byte: the content's own `[]`, one comma between blobs, and each
     * blob at its JSON-escaped length inside the content string. A lone over-budget blob is kept
     * (blobs are indivisible). Always yields at least one (possibly empty) chunk.
     */
    fun chunkBlobs(
        blobs: List<RekeyBlob>,
        envelopeBytes: Int,
    ): List<List<RekeyBlob>> {
        val budget = REKEY_RUMOR_MAX_BYTES - envelopeBytes
        val chunks = ArrayList<List<RekeyBlob>>()
        var current = ArrayList<RekeyBlob>()
        var used = 2 // the content's own "[]"
        for (blob in blobs) {
            val cost = escapedJsonLength(blob)
            if (current.isNotEmpty() && (current.size >= MAX_BLOBS_PER_CHUNK || used + 1 + cost > budget)) {
                chunks.add(current)
                current = ArrayList()
                used = 2
            }
            used += cost + (if (current.isNotEmpty()) 1 else 0)
            current.add(blob)
        }
        if (current.isNotEmpty() || chunks.isEmpty()) chunks.add(current)
        return chunks
    }

    /** A blob's byte length inside the rumor's JSON-escaped `content` string (without outer quotes). */
    private fun escapedJsonLength(blob: RekeyBlob): Int {
        val raw = encodeContent(listOf(blob)).let { it.substring(1, it.length - 1) }
        var extra = 0
        for (c in raw) if (c == '"' || c == '\\') extra++
        return raw.encodeToByteArray().size + extra
    }

    /** Serializes a chunk's blobs into the kind-3303 rumor content. */
    fun encodeContent(blobs: List<RekeyBlob>): String = ConcordJson.instance.encodeToString(ListSerializer(RekeyBlob.serializer()), blobs)

    /** Parses a kind-3303 rumor's content back into its blobs, or empty on error. */
    fun decodeContent(content: String): List<RekeyBlob> =
        try {
            ConcordJson.instance.decodeFromString(ListSerializer(RekeyBlob.serializer()), content)
        } catch (_: Exception) {
            emptyList()
        }

    const val KIND: Int = 3303

    /** CORD-06 §1: a single kind-3303 event carries at most this many per-recipient blobs. */
    const val MAX_BLOBS_PER_CHUNK = 120

    /**
     * Byte ceiling on one kind-3303 rumor's JSON, beside the 120-blob count cap. Base blobs are
     * wider than channel ones, and 120 of them pushed the wrap's NIP-44 plaintext (the seal, which
     * carries the rumor's own NIP-44 ciphertext as base64) past the 65,535-byte cap. 40,960 is the
     * top of the NIP-44 padding bucket that still wraps — the reference client's
     * `REKEY_RUMOR_MAX_BYTES`. Capacity: 120 blobs at 72 bytes (the count cap binds), 99 at 104,
     * 90 at 136. Finer chunking is always wire-legal.
     */
    const val REKEY_RUMOR_MAX_BYTES = 40_960

    /**
     * Builds a rekey blob for one recipient using [rotatorSigner] instead of a raw
     * private key, so a NIP-46 bunker rotator can mint blobs without exposing its
     * key (the wrap is a single `nip44Encrypt` to the recipient). The locator is
     * public-input-only (CORD-06 §2) and needs no signing.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun blobForSigner(
        rotatorSigner: NostrSigner,
        recipientXOnly: ByteArray,
        scopeId: ByteArray,
        newEpoch: Long,
        newKey: ByteArray,
        newControlPk: ByteArray? = null,
        newControlRoot: ByteArray? = null,
    ): RekeyBlob {
        val rotatorXOnly = rotatorSigner.pubKey.hexToByteArray()
        val locator = ConcordKeyDerivation.recipientLocator(rotatorXOnly, recipientXOnly, scopeId, newEpoch).toHexKey()
        val payloadB64 = Base64.Default.encode(RekeyPayload(scopeId, newEpoch, newKey, newControlPk, newControlRoot).encode())
        val wrapped = rotatorSigner.nip44Encrypt(payloadB64, recipientXOnly.toHexKey())
        return RekeyBlob(locator, wrapped)
    }

    /**
     * Finds the recipient's whole decrypted [RekeyPayload] (scope and epoch already
     * verified against the expectation) via [recipientSigner], or null if no blob
     * matches or it fails to open/verify. Base rotations need the full payload —
     * the delivered `new_control_pk` / `new_control_root` ride beside the key.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun findPayloadWithSigner(
        blobs: List<RekeyBlob>,
        recipientSigner: NostrSigner,
        rotatorXOnly: ByteArray,
        scopeId: ByteArray,
        newEpoch: Long,
    ): RekeyPayload? {
        val recipientXOnly = recipientSigner.pubKey.hexToByteArray()
        val myLocator = ConcordKeyDerivation.recipientLocator(rotatorXOnly, recipientXOnly, scopeId, newEpoch).toHexKey()
        val blob = blobs.firstOrNull { it.locator == myLocator } ?: return null
        return try {
            val payload = RekeyPayload.decode(Base64.Default.decode(recipientSigner.nip44Decrypt(blob.wrapped, rotatorXOnly.toHexKey()))) ?: return null
            if (!payload.scopeId.contentEquals(scopeId) || payload.epoch != newEpoch) return null
            payload
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds the recipient's rotated key like [findNewKey], but decrypts the blob via
     * [recipientSigner] (bunker-compatible) rather than a raw private key.
     */
    suspend fun findNewKeyWithSigner(
        blobs: List<RekeyBlob>,
        recipientSigner: NostrSigner,
        rotatorXOnly: ByteArray,
        scopeId: ByteArray,
        newEpoch: Long,
    ): ByteArray? = findPayloadWithSigner(blobs, recipientSigner, rotatorXOnly, scopeId, newEpoch)?.newKey

    /**
     * Finds the recipient's rotated key across the [blobs] of one or more chunks,
     * or null if they were removed. Computes the recipient's locator, matches it,
     * decrypts under the pairwise key, and verifies the payload's scope and epoch.
     *
     * @param recipientPrivKey the recipient's private key
     * @param recipientXOnly   the recipient's x-only pubkey
     * @param rotatorXOnly     the rotator's x-only pubkey
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun findNewKey(
        blobs: List<RekeyBlob>,
        recipientPrivKey: ByteArray,
        recipientXOnly: ByteArray,
        rotatorXOnly: ByteArray,
        scopeId: ByteArray,
        newEpoch: Long,
    ): ByteArray? {
        val myLocator = ConcordKeyDerivation.recipientLocator(rotatorXOnly, recipientXOnly, scopeId, newEpoch).toHexKey()
        val blob = blobs.firstOrNull { it.locator == myLocator } ?: return null
        return try {
            val convKey = Nip44.v2.getConversationKey(recipientPrivKey, rotatorXOnly)
            val payload = RekeyPayload.decode(Base64.Default.decode(Nip44.v2.decrypt(blob.wrapped, convKey))) ?: return null
            if (!payload.scopeId.contentEquals(scopeId) || payload.epoch != newEpoch) return null
            payload.newKey
        } catch (_: Exception) {
            null
        }
    }
}
