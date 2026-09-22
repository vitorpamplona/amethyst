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
package com.vitorpamplona.quartz.cordn.appEncryptedMedia

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * What the blob host is told, asserted field by field.
 *
 * Every one of these is a privacy property that regresses *without failing*:
 * change the content type and the upload still works, the group still reads
 * the file, and the only difference is that a server now knows it is holding a
 * JPEG. There is no downstream check that would catch it, so these assertions
 * are the check.
 */
class CordnBlobUploadTest {
    private val key = ByteArray(32) { it.toByte() }
    private val file = "a private photo".encodeToByteArray()
    private val sealed = CordnMediaEncryption.encrypt(file, key, "image/jpeg", "holiday.jpg")

    @Test
    fun `the host is given the ciphertext, and its hash names the blob`() {
        val blob = CordnBlobUpload.of(sealed)

        // Blossom addresses a blob by the hash of what it stores.
        assertContentEquals(sealed.ciphertext, blob.bytes)
        assertEquals(sha256(sealed.ciphertext).toHexKey(), blob.hash)
        assertEquals(sealed.ciphertext.size.toLong(), blob.length)
    }

    @Test
    fun `the host hash is not the imeta hash`() {
        val blob = CordnBlobUpload.of(sealed)

        // Two hashes on purpose. Uploading under the plaintext hash would let
        // anyone holding the original file prove this account uploaded it, and
        // would make the blob's address a fingerprint of its contents.
        assertNotEquals(sealed.plaintextHash.toHexKey(), blob.hash)
        assertEquals(sha256(file).toHexKey(), sealed.plaintextHash.toHexKey())
    }

    @Test
    fun `the declared type is opaque, never the real one`() {
        val blob = CordnBlobUpload.of(sealed)

        assertEquals("application/octet-stream", blob.contentType)
        assertEquals(CordnBlobUpload.OPAQUE, blob.contentType)
        assertNotEquals(sealed.mimeType, blob.contentType)
    }

    @Test
    fun `the real filename does not reach the host`() {
        val blob = CordnBlobUpload.of(sealed)

        assertEquals(blob.hash, blob.baseFileName)
        assertNotEquals("holiday.jpg", blob.baseFileName)
    }

    @Test
    fun `no alt text and no content warning`() {
        val blob = CordnBlobUpload.of(sealed)

        // Both are plaintext on a Blossom upload. An alt string describing a
        // private photo is that photo's caption, given to the one party that
        // was supposed to see nothing.
        assertNull(blob.alt)
        assertNull(blob.sensitiveContent)
    }

    @Test
    fun `upload, never the media endpoint`() {
        // `/media` asks the server to re-encode. Re-encoding ciphertext
        // destroys it, so an account with "optimize uploads" on would break
        // every attachment and only the recipient would find out.
        assertFalse(CordnBlobUpload.of(sealed).useMediaEndpoint)
    }

    @Test
    fun `the same file uploaded twice is a different blob`() {
        // The nonce is fresh per encryption (see CordnMediaEncryptionTest), so
        // the ciphertext hash differs. A host therefore cannot tell that the
        // same picture was sent to two groups.
        val again = CordnMediaEncryption.encrypt(file, key, "image/jpeg", "holiday.jpg")

        assertNotEquals(CordnBlobUpload.of(sealed).hash, CordnBlobUpload.of(again).hash)
    }
}
