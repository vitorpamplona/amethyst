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

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
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
 * pseudonym (public-input HKDF), and its `wrapped` field is the 72-byte
 * [RekeyPayload] (base64 → NIP-44 under the rotator↔recipient pairwise key). A
 * recipient computes their own locator, finds the matching blob, and decrypts the
 * new key; a member with no matching blob across all chunks of a complete rotation
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
     * Builds a rekey blob delivering [newKey] to one recipient.
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
    ): RekeyBlob {
        val locator = ConcordKeyDerivation.recipientLocator(rotatorXOnly, recipientXOnly, scopeId, newEpoch).toHexKey()
        val payloadB64 = Base64.Default.encode(RekeyPayload(scopeId, newEpoch, newKey).encode())
        val convKey = Nip44.v2.getConversationKey(rotatorPrivKey, recipientXOnly)
        val wrapped = Nip44.v2.encrypt(payloadB64, convKey).encodePayload()
        return RekeyBlob(locator, wrapped)
    }

    /** The kind-3303 rumor tags for a rekey chunk. */
    fun tags(
        scopeId: ByteArray,
        newEpoch: Long,
        prevEpoch: Long,
        prevCommit: HexKey,
        chunkIndex: Int,
        chunkTotal: Int,
    ): Array<Array<String>> =
        arrayOf(
            arrayOf(TAG_SCOPE, scopeId.toHexKey()),
            arrayOf(TAG_NEWEPOCH, newEpoch.toString()),
            arrayOf(TAG_PREVEPOCH, prevEpoch.toString()),
            arrayOf(TAG_PREVCOMMIT, prevCommit),
            arrayOf(TAG_CHUNK, chunkIndex.toString(), chunkTotal.toString()),
        )

    /** Serializes a chunk's blobs into the kind-3303 rumor content. */
    fun encodeContent(blobs: List<RekeyBlob>): String = ConcordJson.instance.encodeToString(ListSerializer(RekeyBlob.serializer()), blobs)

    /** Parses a kind-3303 rumor's content back into its blobs, or empty on error. */
    fun decodeContent(content: String): List<RekeyBlob> =
        try {
            ConcordJson.instance.decodeFromString(ListSerializer(RekeyBlob.serializer()), content)
        } catch (_: Exception) {
            emptyList()
        }

    const val KIND: Int = ConcordKinds.REKEY

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
