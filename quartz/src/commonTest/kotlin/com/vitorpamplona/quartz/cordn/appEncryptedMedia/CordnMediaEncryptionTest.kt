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

import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaEncryption
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CordnMediaEncryptionTest {
    private val key = ByteArray(32) { it.toByte() }
    private val file = "a small photo".encodeToByteArray()

    private fun roundTrip(
        mime: String = "image/jpeg",
        name: String = "photo.jpg",
    ): CordnEncryptedMedia = CordnMediaEncryption.encrypt(file, key, mime, name)

    @Test
    fun `a file round-trips`() {
        val sealed = roundTrip()

        val opened =
            CordnMediaEncryption.decrypt(
                sealed.ciphertext,
                key,
                sealed.nonce,
                sealed.plaintextHash,
                sealed.mimeType,
                sealed.filename,
            )

        assertContentEquals(file, opened)
        assertContentEquals(sha256(file), sealed.plaintextHash)
    }

    @Test
    fun `the same file encrypted twice never reuses a nonce`() {
        // The property this codec leans on harder than MIP-04 does: cordn uses
        // the exporter output directly, so every file in an epoch shares one
        // key and the nonce is all that separates two keystreams.
        val nonces = (1..64).map { roundTrip().nonce.toList() }.toSet()

        assertEquals(64, nonces.size, "a nonce repeated under a shared key is a keystream break")
    }

    @Test
    fun `renaming a file breaks it, because the name is authenticated`() {
        val sealed = roundTrip(name = "photo.jpg")

        assertFailsWith<Exception> {
            CordnMediaEncryption.decrypt(sealed.ciphertext, key, sealed.nonce, sealed.plaintextHash, sealed.mimeType, "invoice.pdf")
        }
    }

    @Test
    fun `changing the declared type breaks it`() {
        val sealed = roundTrip(mime = "image/jpeg")

        assertFailsWith<Exception> {
            CordnMediaEncryption.decrypt(sealed.ciphertext, key, sealed.nonce, sealed.plaintextHash, "application/pdf", sealed.filename)
        }
    }

    @Test
    fun `a hash that does not describe the plaintext is refused`() {
        val sealed = roundTrip()
        val lying = sealed.plaintextHash.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertFailsWith<Exception> {
            CordnMediaEncryption.decrypt(sealed.ciphertext, key, sealed.nonce, lying, sealed.mimeType, sealed.filename)
        }
    }

    @Test
    fun `a cordn blob is not a Marmot blob`() {
        // §4.5: same exporter context word, different key derivation and
        // different AAD. Confusing the two must fail loudly rather than
        // produce plausible garbage, and this is the test that says so.
        val sealed = roundTrip()

        assertFailsWith<Exception> {
            Mip04MediaEncryption.decrypt(
                sealed.ciphertext,
                key,
                sealed.nonce,
                sealed.plaintextHash,
                sealed.mimeType,
                sealed.filename,
            )
        }
    }

    @Test
    fun `the cordn file key is not the Marmot file key for the same inputs`() {
        val marmot = Mip04MediaEncryption.deriveFileKey(key, sha256(file), "image/jpeg", "photo.jpg")

        // cordn uses the exporter output as-is; Marmot expands it. If these
        // ever matched, one of the two implementations would be wrong.
        assertNotEquals(key.toList(), marmot.toList())
    }

    @Test
    fun `an imeta tag round-trips through parse`() {
        val sealed = roundTrip()
        val tag = CordnMediaTag.build(sealed, key, url = "https://blossom.example.com/abc", dimensions = "800x600")

        val parsed = CordnMediaTag.parseAll(arrayOf(tag)).single()

        assertEquals("https://blossom.example.com/abc", parsed.url)
        assertEquals(sealed.mimeType, parsed.mimeType)
        assertEquals(sealed.filename, parsed.filename)
        assertContentEquals(sealed.nonce, parsed.nonceBytes)
        assertContentEquals(sealed.plaintextHash, parsed.hashBytes)
        assertEquals("800x600", parsed.dimensions)
        assertTrue(parsed.isImage)
        assertFalse(parsed.isAudio)
    }

    @Test
    fun `a half-formed attachment is dropped rather than half-parsed`() {
        // The only thing a caller could do with a partial descriptor is start
        // a decrypt that must fail. A message with one broken attachment
        // should still show its other attachments and its text.
        val good = CordnMediaTag.build(roundTrip(), key, url = "https://blossom.example.com/ok")
        val noNonce = arrayOf("imeta", "url https://blossom.example.com/bad", "m image/png", "filename x.png", "x " + "ab".repeat(32))
        val shortNonce = arrayOf("imeta", "url https://blossom.example.com/bad", "m image/png", "filename x.png", "x " + "ab".repeat(32), "n abcd")

        val parsed = CordnMediaTag.parseAll(arrayOf(noNonce, good, shortNonce))

        assertEquals(1, parsed.size)
        assertEquals("https://blossom.example.com/ok", parsed.single().url)
    }

    @Test
    fun `a filename containing a space survives the tag`() {
        // imeta fields are "key value" strings, so only the FIRST space
        // separates them. Splitting on every space would truncate any filename
        // a person actually typed.
        val sealed = roundTrip(name = "my holiday photo.jpg")
        val parsed = CordnMediaTag.parseAll(arrayOf(CordnMediaTag.build(sealed, key, url = "https://b.example.com/x"))).single()

        assertEquals("my holiday photo.jpg", parsed.filename)
    }

    @Test
    fun `an attachment survives the epoch it was sent in`() {
        // The bug this replaced: the file key was the group's CURRENT epoch
        // exporter, and cordn retains no past epochs — so every attachment
        // became permanently unopenable at the next Commit, for the sender
        // too, with nothing failing until someone scrolled back.
        //
        // The key now travels with the descriptor, so opening a file needs
        // only the message that announced it. Nothing here touches a group at
        // all, which IS the property: there is no longer anything epoch-shaped
        // for the decrypt path to depend on.
        val fileKey = CordnMediaEncryption.newFileKey()
        val sealed = CordnMediaEncryption.encrypt(file, fileKey, "image/jpeg", "photo.jpg")
        val tag = CordnMediaTag.build(sealed, fileKey, url = "https://b.example.com/y")

        val parsed = CordnMediaTag.parseAll(arrayOf(tag)).single()
        val opened =
            CordnMediaEncryption.decrypt(
                sealed.ciphertext,
                parsed.fileKeyBytes,
                parsed.nonceBytes,
                parsed.hashBytes,
                parsed.mimeType,
                parsed.filename,
            )

        assertContentEquals(file, opened)
    }

    @Test
    fun `two files never share a key`() {
        // One key per epoch used to mean the nonce was the only thing keeping
        // two files' keystreams apart, so a repeated nonce broke every file in
        // the epoch at once. Per-file keys retire that failure mode.
        val a = CordnMediaEncryption.newFileKey()
        val b = CordnMediaEncryption.newFileKey()

        assertFalse(a.contentEquals(b))
        assertEquals(CordnMediaEncryption.KEY_LENGTH, a.size)
    }

    @Test
    fun `an imeta without a key is dropped rather than half-parsed`() {
        // A descriptor with no key cannot open anything, so returning it would
        // hand the UI an attachment whose only possible outcome is a failed
        // decrypt.
        val tag = CordnMediaTag.build(roundTrip(), key, url = "https://b.example.com/z")
        val stripped = tag.filterNot { it.startsWith("${CordnMediaTag.KEY} ") }.toTypedArray()

        assertTrue(CordnMediaTag.parseAll(arrayOf(stripped)).isEmpty())
    }
}
