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
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageCipher
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption
import com.vitorpamplona.quartz.marmot.mip01Groups.Mip01ImageCrypto
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarmotGroupImageTest {
    private val nostrGroupId = "aa".repeat(32)
    private val plaintext = "PNGDATA-a-fake-avatar-image-payload".encodeToByteArray()

    private val emptyAad = ByteArray(0)

    // ------------------------------------------------------------ encryption (MIP-01 v2)

    @Test
    fun encrypt_thenDecrypt_roundTrips() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)

        assertEquals(MarmotGroupImageEncryption.KEY_LENGTH, enc.imageKey.size)
        assertEquals(MarmotGroupImageEncryption.NONCE_LENGTH, enc.imageNonce.size)

        val decrypted = MarmotGroupImageEncryption.decrypt(enc.ciphertext, enc.imageKey, enc.imageNonce)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun imageHash_isSha256OfCiphertext() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)
        assertEquals(sha256(enc.ciphertext).toHexKey(), enc.imageHash)
    }

    /**
     * Byte-for-byte interop guard: the AEAD key MUST be
     * HKDF(image_key, "mip01-image-encryption-v2") with empty AAD — exactly what mdk's
     * group_image.rs does. If this drifts, Amethyst and whitenoise stop interoperating.
     */
    @Test
    fun scheme_matchesMdk_hkdfSeedAndEmptyAad() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)
        val derivedKey = Mip01ImageCrypto.deriveImageEncryptionKey(enc.imageKey)
        val manual = ChaCha20Poly1305.decrypt(enc.ciphertext, emptyAad, enc.imageNonce, derivedKey)
        assertContentEquals(plaintext, manual)
    }

    @Test
    fun decrypt_wrongSeed_fails() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)
        assertFailsWith<IllegalStateException> {
            MarmotGroupImageEncryption.decrypt(enc.ciphertext, RandomInstance.bytes(32), enc.imageNonce)
        }
    }

    @Test
    fun decryptAny_v2_succeeds() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)
        val out = MarmotGroupImageEncryption.decryptAny(enc.ciphertext, enc.imageKey, enc.imageNonce)
        assertNotNull(out)
        assertContentEquals(plaintext, out)
    }

    @Test
    fun decryptAny_fallsBackToV1RawKey() {
        // v1: image_key is used directly as the AEAD key (no HKDF), empty AAD — mdk's fallback.
        val rawKey = RandomInstance.bytes(32)
        val nonce = RandomInstance.bytes(12)
        val v1Blob = ChaCha20Poly1305.encrypt(plaintext, emptyAad, nonce, rawKey)

        val out = MarmotGroupImageEncryption.decryptAny(v1Blob, rawKey, nonce)
        assertNotNull(out)
        assertContentEquals(plaintext, out)
    }

    @Test
    fun decryptAny_garbage_returnsNull() {
        val out =
            MarmotGroupImageEncryption.decryptAny(
                RandomInstance.bytes(64),
                RandomInstance.bytes(32),
                RandomInstance.bytes(12),
            )
        assertNull(out)
    }

    @Test
    fun uploadKeypairSecret_isDeterministicAndDistinctFromSeed() {
        val seed = RandomInstance.bytes(32)
        val s1 = MarmotGroupImageEncryption.deriveUploadKeypairSecret(seed)
        val s2 = MarmotGroupImageEncryption.deriveUploadKeypairSecret(seed)
        assertEquals(32, s1.size)
        assertContentEquals(s1, s2)
        assertTrue(!s1.contentEquals(seed), "upload secret must be derived, not the raw seed")
    }

    @Test
    fun cipher_encryptDecrypt_roundTrips_asUsedByUploadAndDisplay() {
        val uploadCipher = MarmotGroupImageCipher.forNewImage()
        val blob = uploadCipher.encrypt(plaintext)

        val displayCipher = MarmotGroupImageCipher(uploadCipher.imageKey, uploadCipher.imageNonce)
        assertContentEquals(plaintext, displayCipher.decrypt(blob))
        assertContentEquals(plaintext, displayCipher.decryptOrNull(blob))
    }

    @Test
    fun cipher_decryptOrNull_wrongSeed_returnsNull() {
        val uploadCipher = MarmotGroupImageCipher.forNewImage()
        val blob = uploadCipher.encrypt(plaintext)
        val wrong = MarmotGroupImageCipher(RandomInstance.bytes(32), uploadCipher.imageNonce)
        assertNull(wrong.decryptOrNull(blob))
    }

    // ------------------------------------------------------------ wire format

    @Test
    fun wire_roundTrips_withImage() {
        val original =
            MarmotGroupData(
                version = 2,
                nostrGroupId = nostrGroupId,
                name = "Otters",
                description = "river friends",
                adminPubkeys = listOf("bb".repeat(32)),
                relays = listOf("wss://relay.example/"),
                imageHash = "cc".repeat(32),
                imageKey = "dd".repeat(32).hexToByteArray(),
                imageNonce = "ee".repeat(12).hexToByteArray(),
                imageUploadKey = "ff".repeat(32).hexToByteArray(),
            )

        val decoded = assertNotNull(MarmotGroupData.decodeTls(original.encodeTls()))
        assertEquals("Otters", decoded.name)
        assertEquals("cc".repeat(32), decoded.imageHash)
        assertContentEquals("dd".repeat(32).hexToByteArray(), decoded.imageKey)
        assertContentEquals("ee".repeat(12).hexToByteArray(), decoded.imageNonce)
        assertContentEquals("ff".repeat(32).hexToByteArray(), decoded.imageUploadKey)
        assertNull(decoded.disappearingMessageSecs)
        assertTrue(decoded.hasImage())
    }

    @Test
    fun wire_roundTrips_withoutImage() {
        val original =
            MarmotGroupData(
                version = 2,
                nostrGroupId = nostrGroupId,
                name = "Plain",
                adminPubkeys = listOf("bb".repeat(32)),
                relays = listOf("wss://relay.example/"),
            )
        val decoded = assertNotNull(MarmotGroupData.decodeTls(original.encodeTls()))
        assertEquals("Plain", decoded.name)
        assertNull(decoded.imageHash)
        assertTrue(!decoded.hasImage())
    }

    /**
     * INTEROP GUARD: at version 2, a group WITH an image must serialize with the image
     * fields as the LAST fields and ZERO trailing bytes — exactly what mdk-core's
     * `TlsNostrGroupDataExtensionV1V2` parser consumes. mdk rejects any trailing bytes at a
     * known version, so a stray byte here silently breaks the group for whitenoise/mdk
     * members. This test reproduces mdk's v1/v2 field consumption and asserts nothing is
     * left over.
     */
    @Test
    fun wire_v2WithImage_hasNoTrailingBytesForMdk() {
        val withImage =
            MarmotGroupData(
                version = 2,
                nostrGroupId = nostrGroupId,
                name = "Compat",
                description = "d",
                adminPubkeys = listOf("bb".repeat(32)),
                relays = listOf("wss://relay.example/"),
                imageHash = "cc".repeat(32),
                imageKey = "dd".repeat(32).hexToByteArray(),
                imageNonce = "ee".repeat(12).hexToByteArray(),
                imageUploadKey = "ff".repeat(32).hexToByteArray(),
            )

        val reader = TlsReader(withImage.encodeTls())
        reader.readUint16() // version
        reader.readBytes(32) // nostr_group_id
        reader.readOpaqueVarInt() // name
        reader.readOpaqueVarInt() // description
        reader.readOpaqueVarInt() // admin_pubkeys
        reader.readOpaqueVarInt() // relays
        reader.readOpaqueVarInt() // image_hash
        reader.readOpaqueVarInt() // image_key
        reader.readOpaqueVarInt() // image_nonce
        reader.readOpaqueVarInt() // image_upload_key
        assertTrue(
            !reader.hasRemaining,
            "v2 image extension has trailing bytes past image_upload_key → mdk would reject it",
        )
    }

    @Test
    fun wire_roundTrips_v3Disappearing_withImage() {
        val original =
            MarmotGroupData(
                version = 3,
                nostrGroupId = nostrGroupId,
                name = "Ephemeral",
                adminPubkeys = listOf("bb".repeat(32)),
                relays = emptyList(),
                imageHash = "cc".repeat(32),
                imageKey = "dd".repeat(32).hexToByteArray(),
                imageNonce = "ee".repeat(12).hexToByteArray(),
                imageUploadKey = "ff".repeat(32).hexToByteArray(),
                disappearingMessageSecs = 3600UL,
            )
        val decoded = assertNotNull(MarmotGroupData.decodeTls(original.encodeTls()))
        assertEquals(3600UL, decoded.disappearingMessageSecs)
        assertTrue(decoded.hasImage())
    }

    @Test
    fun withImage_andWithoutImage_helpers() {
        val base =
            MarmotGroupData(
                nostrGroupId = nostrGroupId,
                name = "Base",
                adminPubkeys = listOf("bb".repeat(32)),
            )
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)
        val withImg = base.withImage(enc.imageHash, enc.imageKey, enc.imageNonce, RandomInstance.bytes(32))
        assertTrue(withImg.hasImage())

        val cleared = withImg.withoutImage()
        assertTrue(!cleared.hasImage())
        assertNull(cleared.imageUploadKey)
    }
}
