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

    // ------------------------------------------------------------ encryption

    @Test
    fun encrypt_thenDecrypt_roundTrips() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext, "image/png")

        assertEquals(MarmotGroupImageEncryption.KEY_LENGTH, enc.imageKey.size)
        assertEquals(MarmotGroupImageEncryption.NONCE_LENGTH, enc.imageNonce.size)

        val decrypted =
            MarmotGroupImageEncryption.decrypt(enc.ciphertext, enc.imageKey, enc.imageNonce, "image/png")
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun imageHash_isSha256OfCiphertext() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext, "image/jpeg")
        assertEquals(sha256(enc.ciphertext).toHexKey(), enc.imageHash)
    }

    @Test
    fun mediaType_isCanonicalizedInAad() {
        // "IMAGE/PNG; charset=binary" canonicalizes to "image/png" — must still decrypt with "image/png".
        val enc = MarmotGroupImageEncryption.encrypt(plaintext, "IMAGE/PNG; charset=binary")
        val decrypted =
            MarmotGroupImageEncryption.decrypt(enc.ciphertext, enc.imageKey, enc.imageNonce, "image/png")
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_wrongMediaType_fails() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext, "image/png")
        assertFailsWith<IllegalStateException> {
            MarmotGroupImageEncryption.decrypt(enc.ciphertext, enc.imageKey, enc.imageNonce, "image/jpeg")
        }
    }

    @Test
    fun decryptAny_canonical_succeeds() {
        val enc = MarmotGroupImageEncryption.encrypt(plaintext, "image/webp")
        val out =
            MarmotGroupImageEncryption.decryptAny(enc.ciphertext, enc.imageKey, enc.imageNonce, "image/webp")
        assertNotNull(out)
        assertContentEquals(plaintext, out)
    }

    @Test
    fun decryptAny_fallsBackToDeprecatedHkdfScheme() {
        // Produce a blob with the DEPRECATED scheme: image_key is an HKDF seed, no AAD.
        val seed = RandomInstance.bytes(32)
        val nonce = RandomInstance.bytes(12)
        val legacyKey = Mip01ImageCrypto.deriveImageEncryptionKey(seed)
        val legacyBlob = ChaCha20Poly1305.encrypt(plaintext, ByteArray(0), nonce, legacyKey)

        // decryptAny tries canonical first (seed-as-raw-key + media AAD → fails), then legacy.
        val out = MarmotGroupImageEncryption.decryptAny(legacyBlob, seed, nonce, "image/png")
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
                "image/png",
            )
        assertNull(out)
    }

    @Test
    fun cipher_encryptDecrypt_roundTrips_asUsedByUploadAndDisplay() {
        // The upload path builds a fresh cipher, encrypts, and stores its key/nonce;
        // the display path rebuilds the same cipher from those fields and decrypts.
        val uploadCipher = MarmotGroupImageCipher.forNewImage("image/png")
        val blob = uploadCipher.encrypt(plaintext)

        val displayCipher = MarmotGroupImageCipher(uploadCipher.imageKey, uploadCipher.imageNonce, "image/png")
        assertContentEquals(plaintext, displayCipher.decrypt(blob))
        assertContentEquals(plaintext, displayCipher.decryptOrNull(blob))
    }

    @Test
    fun cipher_decryptOrNull_wrongKey_returnsNull() {
        val uploadCipher = MarmotGroupImageCipher.forNewImage("image/png")
        val blob = uploadCipher.encrypt(plaintext)
        val wrong = MarmotGroupImageCipher(RandomInstance.bytes(32), uploadCipher.imageNonce, "image/png")
        assertNull(wrong.decryptOrNull(blob))
    }

    // ------------------------------------------------------------ wire format

    @Test
    fun wire_roundTrips_withImageAndMediaType() {
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
                imageMediaType = "image/png",
            )

        val decoded = assertNotNull(MarmotGroupData.decodeTls(original.encodeTls()))
        assertEquals("Otters", decoded.name)
        assertEquals("river friends", decoded.description)
        assertEquals("cc".repeat(32), decoded.imageHash)
        assertContentEquals("dd".repeat(32).hexToByteArray(), decoded.imageKey)
        assertContentEquals("ee".repeat(12).hexToByteArray(), decoded.imageNonce)
        assertContentEquals("ff".repeat(32).hexToByteArray(), decoded.imageUploadKey)
        assertEquals("image/png", decoded.imageMediaType)
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
        assertNull(decoded.imageMediaType)
        assertNull(decoded.imageHash)
        assertTrue(!decoded.hasImage())
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
                imageMediaType = "image/jpeg",
                disappearingMessageSecs = 3600UL,
            )
        val decoded = assertNotNull(MarmotGroupData.decodeTls(original.encodeTls()))
        assertEquals(3600UL, decoded.disappearingMessageSecs)
        assertEquals("image/jpeg", decoded.imageMediaType)
        assertTrue(decoded.hasImage())
    }

    @Test
    fun wire_v2WithMediaType_readByLegacyDecoderIgnoresTrailing() {
        // Emitting media_type at v2 forces an empty disappearing field to keep alignment.
        // A reader that stops at disappearing (older logic) must still parse cleanly and
        // see no disappearing timer.
        val withImage =
            MarmotGroupData(
                version = 2,
                nostrGroupId = nostrGroupId,
                name = "Compat",
                adminPubkeys = listOf("bb".repeat(32)),
                relays = emptyList(),
                imageHash = "cc".repeat(32),
                imageKey = "dd".repeat(32).hexToByteArray(),
                imageNonce = "ee".repeat(12).hexToByteArray(),
                imageUploadKey = "ff".repeat(32).hexToByteArray(),
                imageMediaType = "image/png",
            )
        val decoded = assertNotNull(MarmotGroupData.decodeTls(withImage.encodeTls()))
        assertNull(decoded.disappearingMessageSecs)
        assertEquals("image/png", decoded.imageMediaType)
    }

    @Test
    fun withImage_andWithoutImage_helpers() {
        val base =
            MarmotGroupData(
                nostrGroupId = nostrGroupId,
                name = "Base",
                adminPubkeys = listOf("bb".repeat(32)),
            )
        val enc = MarmotGroupImageEncryption.encrypt(plaintext, "image/png")
        val withImg =
            base.withImage(enc.imageHash, enc.imageKey, enc.imageNonce, RandomInstance.bytes(32), "image/png")
        assertTrue(withImg.hasImage())
        assertEquals("image/png", withImg.imageMediaType)

        val cleared = withImg.withoutImage()
        assertTrue(!cleared.hasImage())
        assertNull(cleared.imageMediaType)
        assertNull(cleared.imageUploadKey)
    }
}
