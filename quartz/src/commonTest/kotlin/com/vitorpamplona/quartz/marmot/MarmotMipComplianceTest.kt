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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip01Groups.Mip01ImageCrypto
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaEncryption
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04ParseResult
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.parseMip04
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the Marmot Protocol MIP compliance fixes landing alongside
 * this test file. Covers:
 *
 * - MIP-01: `MarmotGroupData` v3 round-trip, `disappearing_message_secs`
 *   validation, version rejection, image/Blossom HKDF helpers.
 * - MIP-04: `Mip04ParseResult.DeprecatedV1` emission and deprecation warning
 *   for legacy v1 imeta tags.
 */
class MarmotMipComplianceTest {
    private val groupId32 = "0".repeat(64)
    private val adminPubkey = "a".repeat(64)

    // ---------------------------------------------------------------------- MIP-01

    @Test
    fun marmotGroupData_defaultVersionIsThree() {
        assertEquals(3, MarmotGroupData.CURRENT_VERSION)
    }

    @Test
    fun marmotGroupData_roundTripWithoutDisappearing() {
        val original =
            MarmotGroupData(
                nostrGroupId = groupId32,
                name = "test group",
                description = "desc",
                adminPubkeys = listOf(adminPubkey),
                relays = listOf("wss://relay.example/"),
            )

        val bytes = original.encodeTls()
        val decoded = assertNotNull(MarmotGroupData.decodeTls(bytes))

        assertEquals(original.version, decoded.version)
        assertEquals(original.nostrGroupId, decoded.nostrGroupId)
        assertEquals(original.name, decoded.name)
        assertEquals(original.description, decoded.description)
        assertEquals(original.adminPubkeys, decoded.adminPubkeys)
        assertEquals(original.relays, decoded.relays)
        assertNull(decoded.disappearingMessageSecs)
    }

    @Test
    fun marmotGroupData_roundTripWithDisappearingSecs() {
        val original =
            MarmotGroupData(
                nostrGroupId = groupId32,
                adminPubkeys = listOf(adminPubkey),
                relays = listOf("wss://relay.example/"),
                disappearingMessageSecs = 86_400UL,
            )

        val bytes = original.encodeTls()
        val decoded = assertNotNull(MarmotGroupData.decodeTls(bytes))

        assertEquals(86_400UL, decoded.disappearingMessageSecs)
    }

    @Test
    fun marmotGroupData_rejectsZeroDisappearingSecsInConstructor() {
        assertFailsWith<IllegalArgumentException> {
            MarmotGroupData(
                nostrGroupId = groupId32,
                disappearingMessageSecs = 0UL,
            )
        }
    }

    @Test
    fun marmotGroupData_rejectsZeroDisappearingSecsOnDecode() {
        // Hand-crafted TLS blob: version=3, group_id=32x0, empty opaque2 for
        // name/description/admins/relays/images, then disappearing_message_secs
        // = 8 bytes of zero (invalid).
        val header =
            ByteArray(2 + 32) {
                // version + groupId
                when (it) {
                    0 -> 0

                    1 -> 3

                    // version=3
                    else -> 0
                }
            }
        // 8x opaque2 fields of length 0, each encoded as two zero bytes:
        //   name, description, admin_pubkeys, relays, image_hash, image_key,
        //   image_nonce, image_upload_key
        val zeroFields = ByteArray(8 * 2) // all zeros
        // disappearing_message_secs opaque2 with 8 zero bytes
        val disappearingField = ByteArray(2 + 8).also { it[1] = 8 }
        val blob = header + zeroFields + disappearingField

        // decodeTls catches any exception and returns null
        assertNull(MarmotGroupData.decodeTls(blob))
    }

    @Test
    fun marmotGroupData_rejectsUnsupportedVersion() {
        // version=99 is not in SUPPORTED_VERSIONS
        val header =
            ByteArray(2 + 32).also {
                it[0] = 0
                it[1] = 99
            }
        val zeroFields = ByteArray(8 * 2) // name..image_upload_key
        val disappearingField = ByteArray(2) // zero-length
        val blob = header + zeroFields + disappearingField

        assertNull(MarmotGroupData.decodeTls(blob))
    }

    @Test
    fun mip01ImageCrypto_deriveImageKeyIs32BytesAndDeterministic() {
        val seed = "11".repeat(32).hexToByteArray()

        val k1 = Mip01ImageCrypto.deriveImageEncryptionKey(seed)
        val k2 = Mip01ImageCrypto.deriveImageEncryptionKey(seed)

        assertEquals(32, k1.size)
        assertContentEquals(k1, k2)
    }

    @Test
    fun mip01ImageCrypto_imageAndUploadKeysDiffer() {
        val seed = "22".repeat(32).hexToByteArray()

        val imageKey = Mip01ImageCrypto.deriveImageEncryptionKey(seed)
        val uploadSeed = Mip01ImageCrypto.deriveBlossomUploadSeed(seed)

        assertEquals(32, imageKey.size)
        assertEquals(32, uploadSeed.size)
        assertTrue(
            !imageKey.contentEquals(uploadSeed),
            "Distinct HKDF labels must produce different outputs",
        )
    }

    @Test
    fun mip01ImageCrypto_rejectsWrongSeedLength() {
        val short = ByteArray(16)
        assertFailsWith<IllegalArgumentException> { Mip01ImageCrypto.deriveImageEncryptionKey(short) }
        assertFailsWith<IllegalArgumentException> { Mip01ImageCrypto.deriveBlossomUploadSeed(short) }
    }

    // ---------------------------------------------------------------------- MIP-04

    @Test
    fun mip04_parsesValidV2Tag() {
        val nonceHex = "00".repeat(12)
        val fileHashHex = "33".repeat(32)
        val tag =
            IMetaTagBuilder("https://blobs.example/abc")
                .add("m", "image/jpeg")
                .add("filename", "photo.jpg")
                .add("x", fileHashHex)
                .add("n", nonceHex)
                .add("v", Mip04MediaEncryption.VERSION)
                .build()

        val parsed = tag.parseMip04()
        assertIs<Mip04ParseResult.Parsed>(parsed)
        assertEquals(Mip04MediaEncryption.VERSION, parsed.meta.version)
        assertEquals("photo.jpg", parsed.meta.filename)
    }

    @Test
    fun mip04_rejectsDeprecatedV1Tag() {
        val nonceHex = "00".repeat(12)
        val fileHashHex = "44".repeat(32)
        val tag =
            IMetaTagBuilder("https://blobs.example/def")
                .add("m", "image/png")
                .add("filename", "old.png")
                .add("x", fileHashHex)
                .add("n", nonceHex)
                .add("v", Mip04MediaEncryption.LEGACY_VERSION_V1)
                .build()

        val parsed = tag.parseMip04()
        assertIs<Mip04ParseResult.DeprecatedV1>(parsed)
        assertEquals("https://blobs.example/def", parsed.url)
    }

    @Test
    fun mip04_rejectsUnknownVersion() {
        val nonceHex = "00".repeat(12)
        val fileHashHex = "55".repeat(32)
        val tag =
            IMetaTagBuilder("https://blobs.example/xyz")
                .add("m", "image/png")
                .add("filename", "future.png")
                .add("x", fileHashHex)
                .add("n", nonceHex)
                .add("v", "mip04-v99")
                .build()

        assertIs<Mip04ParseResult.Invalid>(tag.parseMip04())
    }

    @Test
    fun mip04_rejectsWrongNonceLength() {
        // Only 22 hex chars = 11 bytes, below the required 12.
        val shortNonce = "00".repeat(11)
        val fileHashHex = "66".repeat(32)
        val tag =
            IMetaTagBuilder("https://blobs.example/nonce-bad")
                .add("m", "image/png")
                .add("filename", "bad.png")
                .add("x", fileHashHex)
                .add("n", shortNonce)
                .add("v", Mip04MediaEncryption.VERSION)
                .build()

        assertIs<Mip04ParseResult.Invalid>(tag.parseMip04())
    }
}
