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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConcordRekeyTest {
    private val rotator = KeyPair()
    private val alice = KeyPair()
    private val bob = KeyPair()
    private val carol = KeyPair() // removed member

    private val scope = ByteArray(32) { 0x42 }
    private val newEpoch = 1L
    private val newKey = ByteArray(32) { 0x7E }

    private fun blobFor(recipient: KeyPair) = ConcordRekey.blobFor(rotator.privKey!!, rotator.pubKey, recipient.pubKey, scope, newEpoch, newKey)

    private fun find(
        recipient: KeyPair,
        blobs: List<RekeyBlob>,
        epoch: Long = newEpoch,
    ) = ConcordRekey.findNewKey(blobs, recipient.privKey!!, recipient.pubKey, rotator.pubKey, scope, epoch)

    @Test
    fun payloadEncodesAndDecodes() {
        val decoded = RekeyPayload.decode(RekeyPayload(scope, 42, newKey).encode())
        assertContentEquals(scope, decoded?.scopeId)
        assertEquals(42L, decoded?.epoch)
        assertContentEquals(newKey, decoded?.newKey)
        assertNull(RekeyPayload.decode(ByteArray(70))) // wrong size
    }

    @Test
    fun remainingMembersGetTheKeyAndRemovedMembersDoNot() {
        // Rotator distributes the new key to Alice and Bob, but not Carol.
        val blobs = listOf(blobFor(alice), blobFor(bob))
        val content = ConcordRekey.encodeContent(blobs)
        val roundTripped = ConcordRekey.decodeContent(content)

        assertContentEquals(newKey, find(alice, roundTripped))
        assertContentEquals(newKey, find(bob, roundTripped))
        assertNull(find(carol, roundTripped)) // no blob for Carol ⇒ removed
    }

    @Test
    fun wrongEpochDoesNotMatch() {
        val blobs = listOf(blobFor(alice))
        assertNull(find(alice, blobs, epoch = 2L)) // locator is epoch-bound
    }

    @Test
    fun tagsCarryScopeEpochAndChunk() {
        val tags = ConcordRekey.tags(scope, newEpoch, prevEpoch = 0, prevCommit = "ab".repeat(32), chunkIndex = 1, chunkTotal = 3)
        assertEquals(scope.toHexKey(), tags.first { it[0] == ConcordRekey.TAG_SCOPE }[1])
        assertEquals("1", tags.first { it[0] == ConcordRekey.TAG_NEWEPOCH }[1])
        val chunk = tags.first { it[0] == ConcordRekey.TAG_CHUNK }
        assertEquals("1", chunk[1])
        assertEquals("3", chunk[2])
    }
}
