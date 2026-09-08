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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `marmot.group.blossom.image.v1` encryption scheme.
 *
 * The spec says fixed vectors ship later with the conformance fixtures, so
 * these pin the three properties that differ from the MIP-01 scheme instead of
 * asserting against published bytes.
 */
class GroupBlossomImageCryptoTest {
    private val plaintext = ByteArray(512) { (it * 7).toByte() }

    @Test
    fun roundTripsAndPopulatesTheComponent() {
        val encrypted = GroupBlossomImageCrypto.encrypt(plaintext, "image/png")
        val component = encrypted.component

        assertTrue(component.hasImage)
        assertEquals("image/png", component.mediaType)
        assertEquals(32, component.imageKey!!.size)
        assertEquals(12, component.imageNonce!!.size)
        assertEquals(32, component.imageUploadKey!!.size)
        assertContentEquals(sha256(encrypted.ciphertext), component.imageHash)

        assertContentEquals(plaintext, GroupBlossomImageCrypto.decrypt(encrypted.ciphertext, component))
        // Component state survives a wire round trip with the blob still readable.
        val reparsed = GroupBlossomImageV1.decode(component.encode())
        assertContentEquals(plaintext, GroupBlossomImageCrypto.decrypt(encrypted.ciphertext, reparsed))
    }

    @Test
    fun imageKeyIsTheAeadKeyNotAnHkdfSeed() {
        // MIP-01 derived the AEAD key from image_key. Here it IS the key, so
        // decrypting with it directly must work — that is the whole break.
        val encrypted = GroupBlossomImageCrypto.encrypt(plaintext, "image/png")
        val component = encrypted.component
        val direct =
            ChaCha20Poly1305.decrypt(
                encrypted.ciphertext,
                GroupBlossomImageV1.aad("image/png"),
                component.imageNonce!!,
                component.imageKey!!,
            )
        assertContentEquals(plaintext, direct)
    }

    @Test
    fun theMediaTypeIsBoundIntoTheAead() {
        // MIP-01 used an empty AAD, so a blob could be replayed as a different
        // type. Claiming the wrong type must now fail authentication.
        val encrypted = GroupBlossomImageCrypto.encrypt(plaintext, "image/png")
        val lying = encrypted.component.copy(mediaType = "image/gif")

        assertNull(GroupBlossomImageCrypto.decryptOrNull(encrypted.ciphertext, lying))
        assertFailsWith<IllegalStateException> { GroupBlossomImageCrypto.decrypt(encrypted.ciphertext, lying) }
    }

    @Test
    fun anEmptyAadDoesNotAuthenticate() {
        // Guards against silently regressing to the MIP-01 AAD.
        val encrypted = GroupBlossomImageCrypto.encrypt(plaintext, "image/png")
        val component = encrypted.component
        assertFailsWith<IllegalStateException> {
            ChaCha20Poly1305.decrypt(
                encrypted.ciphertext,
                ByteArray(0),
                component.imageNonce!!,
                component.imageKey!!,
            )
        }
    }

    @Test
    fun theContentHashIsCheckedBeforeDecrypting() {
        // Addressed by hash: a store returning different bytes is broken or
        // hostile, and finding out through an AEAD failure loses that
        // distinction.
        val encrypted = GroupBlossomImageCrypto.encrypt(plaintext, "image/png")
        val tampered = encrypted.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() }

        val failure =
            assertFailsWith<IllegalArgumentException> {
                GroupBlossomImageCrypto.decrypt(tampered, encrypted.component)
            }
        assertTrue(failure.message!!.contains("image_hash"))
    }

    @Test
    fun everyImageGetsFreshKeyMaterial() {
        // Nonce reuse under one key is catastrophic for ChaCha20-Poly1305, and
        // here each "message" is a whole file.
        val a = GroupBlossomImageCrypto.encrypt(plaintext, "image/png").component
        val b = GroupBlossomImageCrypto.encrypt(plaintext, "image/png").component
        assertTrue(!a.imageKey.contentEquals(b.imageKey))
        assertTrue(!a.imageNonce.contentEquals(b.imageNonce))
        assertTrue(!a.imageUploadKey.contentEquals(b.imageUploadKey))
    }

    @Test
    fun senderAndReceiverCanonicalizeTheMediaTypeIdentically() {
        // The canonical form is what gets stored and bound, so a sender that
        // passes "IMAGE/JPG; charset=binary" and a receiver reading back
        // "image/jpeg" must agree.
        val encrypted = GroupBlossomImageCrypto.encrypt(plaintext, "IMAGE/JPG; charset=binary")
        assertEquals("image/jpeg", encrypted.component.mediaType)
        assertContentEquals(plaintext, GroupBlossomImageCrypto.decrypt(encrypted.ciphertext, encrypted.component))
    }

    @Test
    fun mediaTypeCanonicalizationFollowsTheFrozenAlgorithm() {
        assertEquals("image/png", MarmotMediaType.canonicalize("  IMAGE/PNG ; q=1 "))
        assertEquals("image/jpeg", MarmotMediaType.canonicalize("image/jpg"))
        assertEquals("image/jpeg", MarmotMediaType.canonicalize("IMAGE/JPG"))
        assertEquals("image/jpeg", MarmotMediaType.canonicalize("image/jpeg"))
        assertNull(MarmotMediaType.canonicalize(""))
        assertNull(MarmotMediaType.canonicalize("   "))
        assertNull(MarmotMediaType.canonicalize("png"), "a type with no '/' is unusable")
        assertNull(MarmotMediaType.canonicalize("; charset=x"))
        // ASCII case folding only: a locale-aware lowercase would map the
        // dotted capital I to a dotless one and change the bytes.
        assertEquals("image/iİ", MarmotMediaType.canonicalize("IMAGE/Iİ"))
    }
}
