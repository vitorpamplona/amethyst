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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip01Groups.Mip01ImageCrypto
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaEncryption
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04ParseResult
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.parseMip04
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    // ---------------------------------------------------------------------- MIP-00

    private fun keyPackageEvent(encoding: String): KeyPackageEvent =
        KeyPackageEvent(
            id = "e".repeat(64),
            pubKey = "a".repeat(64),
            createdAt = 1_700_000_000,
            tags =
                arrayOf(
                    arrayOf("d", "0"),
                    arrayOf("encoding", encoding),
                    arrayOf("mls_ciphersuite", "0x0001"),
                    arrayOf("mls_protocol_version", "1.0"),
                    arrayOf("i", "b".repeat(64)),
                    arrayOf("mls_extensions", "0xf2ee", "0x000a"),
                    arrayOf("mls_proposals", "0x000a"),
                    arrayOf("relays", "wss://relay.example/"),
                ),
            content = "dGVzdA==",
            sig = "s".repeat(128),
        )

    @Test
    fun keyPackage_rejectsHexEncodingExplicitly() {
        // MIP-00 §KeyPackage Content Encoding: "base64" is required; hex was
        // the pre-migration encoding and MUST be rejected. A generic
        // "non-base64" test is insufficient because hex was the legacy default
        // and is the one that compliant clients most likely see in the wild.
        assertFalse(
            KeyPackageUtils.isValid(keyPackageEvent(encoding = "hex")),
            "MIP-00 requires rejecting legacy hex encoding on KeyPackage events",
        )
    }

    @Test
    fun keyPackage_rejectsMissingCiphersuite() {
        // MIP-00 §KeyPackage tags: mls_ciphersuite is a required tag. The
        // existing isValid()-coverage checks encoding and content but not
        // this tag — add a direct assertion for it.
        val kp =
            KeyPackageEvent(
                id = "e".repeat(64),
                pubKey = "a".repeat(64),
                createdAt = 1_700_000_000,
                tags =
                    arrayOf(
                        arrayOf("d", "0"),
                        arrayOf("encoding", "base64"),
                        arrayOf("i", "b".repeat(64)),
                        // mls_ciphersuite intentionally omitted
                    ),
                content = "dGVzdA==",
                sig = "s".repeat(128),
            )
        assertFalse(KeyPackageUtils.isValid(kp), "Missing mls_ciphersuite tag must fail MIP-00 validation")
    }

    // ---------------------------------------------------------------------- MIP-03

    /**
     * Locks in that MIP-03 outer encryption uses an EMPTY AAD. Earlier
     * revisions of this code bound `nostr_group_id` into the AAD, which was
     * a silent wire-format divergence from the spec — our own encrypt/decrypt
     * round-trip tests kept passing because both sides agreed on the (wrong)
     * AAD. This test re-decrypts a `GroupEventEncryption`-produced ciphertext
     * by calling ChaCha20-Poly1305 directly with an empty AAD, which MUST
     * succeed per MIP-03. A future accidental reintroduction of a non-empty
     * AAD would surface here immediately.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun mip03_aadIsEmptyByteString() {
        val key = "11".repeat(32).hexToByteArray()
        val plaintext = "mip03 aad check".encodeToByteArray()

        val payloadB64 = GroupEventEncryption.encrypt(plaintext, key)
        val payload = Base64.decode(payloadB64)
        val nonce = payload.copyOfRange(0, GroupEvent.NONCE_LENGTH)
        val ciphertextWithTag = payload.copyOfRange(GroupEvent.NONCE_LENGTH, payload.size)

        // Manual AEAD decrypt with AAD = empty byte string (spec: MIP-03).
        val recovered = ChaCha20Poly1305.decrypt(ciphertextWithTag, ByteArray(0), nonce, key)
        assertContentEquals(plaintext, recovered)

        // Paranoid cross-check: decrypting with a non-empty AAD (e.g. the
        // nostr_group_id used by the pre-fix implementation) MUST fail
        // ChaCha20-Poly1305 authentication. If it succeeds, AAD has
        // silently become non-empty again.
        assertFailsWith<IllegalStateException>(
            message = "AAD must be empty per MIP-03; a non-empty AAD cannot decrypt a spec-compliant ciphertext",
        ) {
            ChaCha20Poly1305.decrypt(
                ciphertextWithTag,
                "wouldBeGroupId".encodeToByteArray(),
                nonce,
                key,
            )
        }
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
