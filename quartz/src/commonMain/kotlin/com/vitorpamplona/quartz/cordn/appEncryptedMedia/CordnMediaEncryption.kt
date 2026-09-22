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

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * cordn's encrypted-media codec.
 *
 * ## Not MIP-04, and not a refactor of it
 *
 * §4.5 of `quartz/plans/2026-09-17-cordn-interop.md` records the divergence.
 * The primitives are shared — ChaCha20-Poly1305, NIP-92 `imeta`, Blossom — and
 * the codecs are not:
 *
 * | | Marmot MIP-04 v2 | cordn |
 * | --- | --- | --- |
 * | file key | `HKDF-Expand(exporter, context)`, per file | random, per file |
 * | AAD | `"mip04-v2"‖0‖hash‖0‖mime‖0‖filename` | `mime‖0‖filename‖0‖hash` |
 *
 * Feeding one's blob to the other produces an authentication failure, which is
 * the correct outcome and the reason this is a separate file rather than a
 * flag on [com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaEncryption].
 *
 * ## Why the key is random rather than derived from the epoch
 *
 * It was the exporter output at first, which is where MIP-04 starts from, and
 * that is wrong for cordn specifically. MIP-04's exporter reaches back through
 * Marmot's retained epoch secrets; cordn retains none, so `exporterSecret`
 * only ever answers for the epoch the group is on **right now**. Every
 * attachment would have become permanently unopenable at the next Commit —
 * for the sender too, silently, until somebody scrolled back far enough to
 * find a broken image.
 *
 * Carrying a per-file key costs nothing because [CordnMediaTag] rides inside
 * the MLS envelope, so the key is already exactly as confidential and exactly
 * as durable as the message that names it. It also removes the sharp edge the
 * old design had: one key per epoch meant the nonce was the only thing
 * separating two files' keystreams, so a repeated nonce was a keystream reuse
 * break across every file in the epoch. Now a nonce is only ever used once,
 * under a key used once.
 */
object CordnMediaEncryption {
    const val KEY_LENGTH = 32
    const val NONCE_LENGTH = 12

    private val NULL = byteArrayOf(0x00)

    /**
     * A fresh 32-byte key for one file.
     *
     * Random, NOT derived from the group's epoch exporter, and the difference
     * is the whole durability story for attachments. `exporterSecret` reads
     * the **current** epoch's schedule and cordn retains no past epochs, so a
     * key derived that way stops existing the moment anyone commits — every
     * photo and voice note sent before the next join or leave would become
     * permanently unopenable, for everyone including the sender, with no error
     * until someone scrolled back.
     *
     * A per-file key costs nothing to carry because the descriptor that holds
     * it ([CordnMediaTag]) rides **inside** the MLS envelope: it is already as
     * confidential as the message, and already as durable. Forward secrecy is
     * unchanged — a new member still cannot read history they were not sent,
     * because they cannot read the messages carrying these keys either.
     */
    fun newFileKey(): ByteArray = RandomInstance.bytes(KEY_LENGTH)

    /**
     * Encrypts [plaintext] for a group, binding it to its type and name.
     *
     * [mimeType] and [filename] are authenticated, not encrypted: a recipient
     * who is handed the same bytes under a different name or type gets an
     * authentication failure rather than a file that opens as something else.
     */
    fun encrypt(
        plaintext: ByteArray,
        fileKey: ByteArray,
        mimeType: String,
        filename: String,
    ): CordnEncryptedMedia {
        require(fileKey.size == KEY_LENGTH) { "a cordn media key is $KEY_LENGTH bytes" }

        val hash = sha256(plaintext)
        val nonce = RandomInstance.bytes(NONCE_LENGTH)
        val ciphertext = ChaCha20Poly1305.encrypt(plaintext, aad(mimeType, filename, hash), nonce, fileKey)

        return CordnEncryptedMedia(
            ciphertext = ciphertext,
            nonce = nonce,
            plaintextHash = hash,
            mimeType = mimeType,
            filename = filename,
        )
    }

    /**
     * Decrypts and then checks the plaintext against [plaintextHash].
     *
     * The AEAD tag already proves the ciphertext was not altered, so the hash
     * check catches the other thing: a sender whose declared hash does not
     * describe what they actually encrypted. That matters because the hash is
     * what a recipient would use to recognise or deduplicate a file, and
     * because it is the one field the AAD binds that the blob store also sees.
     */
    fun decrypt(
        ciphertext: ByteArray,
        fileKey: ByteArray,
        nonce: ByteArray,
        plaintextHash: ByteArray,
        mimeType: String,
        filename: String,
    ): ByteArray {
        require(fileKey.size == KEY_LENGTH) { "a cordn media key is $KEY_LENGTH bytes" }
        require(nonce.size == NONCE_LENGTH) { "a cordn media nonce is $NONCE_LENGTH bytes" }
        require(plaintextHash.size == 32) { "a sha256 hash is 32 bytes" }

        val plaintext = ChaCha20Poly1305.decrypt(ciphertext, aad(mimeType, filename, plaintextHash), nonce, fileKey)
        check(sha256(plaintext).contentEquals(plaintextHash)) {
            "the decrypted file does not match the hash it was sent with"
        }
        return plaintext
    }

    /**
     * `mime ‖ 0x00 ‖ filename ‖ 0x00 ‖ sha256(plaintext)`.
     *
     * The hash is last here and first in MIP-04. There is no reason to prefer
     * either; matching cordn is the whole requirement, and a fixed-length
     * field at the end means the two variable-length ones are still
     * unambiguously separated by their NULs.
     */
    private fun aad(
        mimeType: String,
        filename: String,
        plaintextHash: ByteArray,
    ): ByteArray {
        val mime = mimeType.encodeToByteArray()
        val name = filename.encodeToByteArray()

        val out = ByteArray(mime.size + 1 + name.size + 1 + plaintextHash.size)
        var at = 0
        mime.copyInto(out, at)
        at += mime.size
        NULL.copyInto(out, at)
        at += 1
        name.copyInto(out, at)
        at += name.size
        NULL.copyInto(out, at)
        at += 1
        plaintextHash.copyInto(out, at)
        return out
    }
}

/** One encrypted file, plus everything a recipient needs to open it. */
data class CordnEncryptedMedia(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val plaintextHash: ByteArray,
    val mimeType: String,
    val filename: String,
) {
    // Arrays, so the generated equals/hashCode would compare identity. Written
    // out because a data class that silently means the wrong thing is worse
    // than one without them.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CordnEncryptedMedia) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce) &&
            plaintextHash.contentEquals(other.plaintextHash) &&
            mimeType == other.mimeType &&
            filename == other.filename
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + plaintextHash.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + filename.hashCode()
        return result
    }
}
