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
package com.vitorpamplona.quartz.nip49PrivKeyEnc

import com.vitorpamplona.quartz.nip19Bech32.Bech32Transcription
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip44Encryption.crypto.XChaCha20Poly1305
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * NIP-49 conformance on the JVM. The device test (androidDeviceTest/NIP49Test) covers the
 * same vectors but runs in neither `./gradlew test` nor pre-push, so nothing on the
 * default path proved the spec vectors still decrypt.
 */
class Nip49SpecTest {
    private val nip49 = Nip49()

    private val specNcryptsec = "ncryptsec1qgg9947rlpvqu76pj5ecreduf9jxhselq2nae2kghhvd5g7dgjtcxfqtd67p9m0w57lspw8gsq6yphnm8623nsl8xn9j4jdzz84zm3frztj3z7s35vpzmqf6ksu8r89qk5z2zxfmu5gv8th8wclt0h4p"
    private val specKey = "3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683"

    @Test
    fun decryptsTheSpecVector() {
        assertEquals(specKey, nip49.decrypt(specNcryptsec, "nostr"))
    }

    @Test
    fun specVectorFieldsDecode() {
        val info = Nip49.EncryptedInfo.decodePayload(specNcryptsec)!!
        assertEquals(0x02.toByte(), info.version)
        assertEquals(16.toByte(), info.logn)
        assertEquals(16, info.salt.size)
        assertEquals(24, info.nonce.size)
        assertEquals(48, info.encryptedKey.size)
    }

    @Test
    fun encryptProducesA91BytePayloadWithVersion2() {
        val payload = nip49.encrypt(specKey, "nostr").bechToBytes()
        assertEquals(91, payload.size)
        assertEquals(0x02.toByte(), payload[0])
        assertEquals(16.toByte(), payload[1])
        assertEquals(Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK, payload[2 + 16 + 24])
    }

    @Test
    fun encryptionIsNonDeterministic() {
        assertNotEquals(nip49.encrypt(specKey, "nostr"), nip49.encrypt(specKey, "nostr"))
    }

    @Test
    fun passwordsAreNfkcNormalized() {
        // Spec vector: U+212B U+2126 U+1E9B U+0323 normalizes to U+00C5 U+03A9 U+1E69.
        val typed = "ÅΩẛ̣"
        val normalized = "ÅΩṩ"
        assertNotEquals(typed, normalized)

        val encrypted = nip49.encrypt(specKey, typed, 8, Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK)
        assertEquals(specKey, nip49.decrypt(encrypted, normalized))
    }

    @Test
    fun wrongPasswordIsRejected() {
        assertFailsWith<IllegalStateException> { nip49.decrypt(specNcryptsec, "nostr2") }
    }

    @Test
    fun anyNonEmptyPasswordShapeRoundTrips() {
        // NIP-49 puts no length or character-class rules on the password.
        listOf("a", " ", "  leading and trailing  ", "ção", "🔑🔒", "x".repeat(1000)).forEach {
            val encrypted = nip49.encrypt(specKey, it, 8, Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK)
            assertEquals(specKey, nip49.decrypt(encrypted, it))
        }
    }

    @Test
    fun keysWithNoPositiveSignedByteDecrypt() {
        // Every byte >= 0x80 is negative as a signed Kotlin Byte. A valid key (well below
        // the secp256k1 order), so the correct password must decrypt it.
        val key = "80".repeat(32)
        val encrypted = nip49.encrypt(key, "nostr", 8, Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK)
        assertEquals(key, nip49.decrypt(encrypted, "nostr"))
    }

    @Test
    fun handCopiedNcryptsecDecrypts() {
        val handCopied = Bech32Transcription.groups(specNcryptsec.uppercase()).joinToString("\n") { it.joinToString("-") }
        assertEquals(specKey, nip49.decrypt(Bech32Transcription.normalize(handCopied), "nostr"))
    }

    @Test
    fun passwordLengthCountsNormalizedCodePoints() {
        assertEquals(11, Nip49.passwordLength("x".repeat(11)))
        assertEquals(false, Nip49.isLongEnough("x".repeat(11)))
        assertEquals(true, Nip49.isLongEnough("x".repeat(12)))
        // A surrogate-pair emoji is one character, not two.
        assertEquals(1, Nip49.passwordLength("\uD83D\uDD11"))
        // The spec's normalization vector: 4 code points typed, 3 after NFKC.
        assertEquals(3, Nip49.passwordLength("\u212B\u2126\u1E9B\u0323"))
    }

    @Test
    fun authenticatedButShortCiphertextIsRejected() {
        // A tag-valid payload that encrypts only 16 bytes must not decode as a zero-padded key.
        val password = "nostr"
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(24) { (it + 1).toByte() }
        val ksb = Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK
        val key = SCrypt.scrypt(password.encodeToByteArray(), salt, 2, 8, 1, 32)
        val shortCiphertext = XChaCha20Poly1305.encrypt(ByteArray(16) { 7 }, byteArrayOf(ksb), nonce, key)

        val info = Nip49.EncryptedInfo(Nip49.EncryptedInfo.V, 1, salt, nonce, ksb, shortCiphertext)
        assertFailsWith<IllegalStateException> { nip49.decrypt(info, password) }
    }
}
