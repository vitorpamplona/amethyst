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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePointerTest {
    /**
     * The community icon/banner are CORD-02 §6 [ImagePointer] *objects* on the wire (Concord v2
     * reference client), not URL strings. Typing them as `String` — the old bug — makes the whole
     * MetadataEntity fail to decode, silently dropping the community name too. This pins the object
     * shape decoding correctly, name included.
     */
    @Test
    fun decodesArmadaShapeMetadataWithEncryptedIconObject() {
        val json =
            """
            {
              "name": "NosFabrica",
              "description": "a community",
              "icon":   { "url": "https://media.example/icon.enc",   "key": "${"1a".repeat(32)}", "nonce": "${"2b".repeat(16)}", "hash": "${"3c".repeat(32)}" },
              "banner": { "url": "https://media.example/banner.enc", "key": "${"4d".repeat(32)}", "nonce": "${"5e".repeat(16)}", "hash": "${"6f".repeat(32)}" },
              "relays": ["wss://relay.example/"]
            }
            """.trimIndent()

        val md = ConcordJson.decodeOrNull<MetadataEntity>(json)
        assertNotNull(md, "an object-shaped icon must decode, not fail the whole entity")
        assertEquals("NosFabrica", md.name)
        assertEquals("https://media.example/icon.enc", md.icon?.url)
        assertEquals("2b".repeat(16), md.icon?.nonce)
        assertEquals("https://media.example/banner.enc", md.banner?.url)
        assertTrue(md.icon!!.isResolvable())
    }

    /** A metadata with no images still decodes (both pointers null). */
    @Test
    fun decodesMetadataWithoutImages() {
        val md = ConcordJson.decodeOrNull<MetadataEntity>("""{"name":"NoPics"}""")
        assertNotNull(md)
        assertEquals("NoPics", md.name)
        assertNull(md.icon)
        assertNull(md.banner)
    }

    /** decryptOrNull round-trips AES-256-GCM with the pointer's key/nonce and verifies the plaintext hash. */
    @Test
    fun decryptRoundTripsAndVerifiesHash() {
        val plaintext = "the real PNG bytes".encodeToByteArray()
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(16) { (it + 7).toByte() }
        val ciphertext = AESGCM(key, nonce).encrypt(plaintext)

        val pointer =
            ImagePointer(
                url = "https://media.example/blob",
                key = key.toHexKey(),
                nonce = nonce.toHexKey(),
                hash = sha256(plaintext).toHexKey(),
            )

        assertEquals(plaintext.toHexKey(), pointer.decryptOrNull(ciphertext)?.toHexKey())
    }

    /** A swapped blob (wrong plaintext hash) fails closed — decryptOrNull returns null, never garbage. */
    @Test
    fun tamperedHashFailsClosed() {
        val plaintext = "original".encodeToByteArray()
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(16) { it.toByte() }
        val ciphertext = AESGCM(key, nonce).encrypt(plaintext)

        val wrongHashPointer =
            ImagePointer(
                url = "https://media.example/blob",
                key = key.toHexKey(),
                nonce = nonce.toHexKey(),
                hash = "00".repeat(32), // not the plaintext's hash
            )

        assertNull(wrongHashPointer.decryptOrNull(ciphertext))
    }
}
