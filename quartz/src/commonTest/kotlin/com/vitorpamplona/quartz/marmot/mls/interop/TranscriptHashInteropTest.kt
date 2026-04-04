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
package com.vitorpamplona.quartz.marmot.mls.interop

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS transcript hash computation (RFC 9420 Section 8.2)
 * against IETF test vectors from github.com/mlswg/mls-implementations
 * (transcript-hashes.json).
 *
 * Verifies confirmed and interim transcript hash computation matches
 * the reference implementations.
 */
class TranscriptHashInteropTest {
    private val allVectors: List<TranscriptHashVector> =
        JsonMapper.jsonInstance.decodeFromString<List<TranscriptHashVector>>(
            TestResourceLoader().loadString("mls/transcript-hashes.json"),
        )

    private val vectors: List<TranscriptHashVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testConfirmedTranscriptHash() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 transcript-hash vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val interimBefore = v.interimTranscriptHashBefore.hexToByteArray()
            val authenticatedContent = v.authenticatedContent.hexToByteArray()

            // ConfirmedTranscriptHashInput = wire_format || FramedContent || signature
            // (everything in AuthenticatedContent EXCEPT the confirmation_tag at the end)
            // For SHA-256, confirmation_tag = VarInt(32) + 32 bytes = 33 bytes
            val confirmationTagSize = 1 + MlsCryptoProvider.HASH_OUTPUT_LENGTH
            val confirmedInput = authenticatedContent.copyOfRange(0, authenticatedContent.size - confirmationTagSize)

            // confirmed_transcript_hash = Hash(interim_before || ConfirmedTranscriptHashInput)
            val writer = TlsWriter()
            writer.putBytes(interimBefore)
            writer.putBytes(confirmedInput)
            val confirmedHash = MlsCryptoProvider.hash(writer.toByteArray())

            assertEquals(
                v.confirmedTranscriptHashAfter,
                confirmedHash.toHexKey(),
                "confirmed_transcript_hash mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testInterimTranscriptHash() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 transcript-hash vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val confirmedAfter = v.confirmedTranscriptHashAfter.hexToByteArray()
            val authenticatedContent = v.authenticatedContent.hexToByteArray()

            // InterimTranscriptHashInput = confirmation_tag (last 33 bytes of AuthenticatedContent)
            val confirmationTagSize = 1 + MlsCryptoProvider.HASH_OUTPUT_LENGTH
            val confirmationTag = authenticatedContent.copyOfRange(authenticatedContent.size - confirmationTagSize, authenticatedContent.size)

            // interim_transcript_hash = Hash(confirmed_hash || InterimTranscriptHashInput)
            val writer = TlsWriter()
            writer.putBytes(confirmedAfter)
            writer.putBytes(confirmationTag)
            val interimHash = MlsCryptoProvider.hash(writer.toByteArray())

            assertEquals(
                v.interimTranscriptHashAfter,
                interimHash.toHexKey(),
                "interim_transcript_hash mismatch at vector $idx",
            )
        }
    }
}
