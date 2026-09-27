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

/**
 * Everything a blob host is told about a cordn attachment — which is as close
 * to nothing as an upload allows.
 *
 * A value rather than a call so it can be asserted. Each field below is a
 * privacy decision, and every one of them fails *silently* if it regresses:
 * the upload still succeeds, the group still sees the file, and the only
 * difference is what a server learned. Nothing downstream would notice, so the
 * only thing that can notice is a test — hence a descriptor that is computed
 * once and forwarded verbatim, rather than seven literal arguments at a call
 * site.
 *
 * | told | withheld |
 * | --- | --- |
 * | the ciphertext and its length | the plaintext |
 * | `sha256(ciphertext)` | `sha256(plaintext)`, which rides in the `imeta` |
 * | `application/octet-stream` | the real MIME type |
 * | a hex filename | the real filename |
 * | who uploaded it | which group it is for, and what it is |
 *
 * Blossom addresses a blob by the hash of the bytes it stores, so [hash] must
 * be the **ciphertext** hash. [CordnMediaTag] carries the **plaintext** hash.
 * Two hashes on purpose: the host needs one to name the blob, the group needs
 * the other to know it received the file that was sent, and neither can be
 * derived from the other.
 */
data class CordnBlobUpload(
    /** What the host stores. */
    val bytes: ByteArray,
    /** `sha256(bytes)`, hex — how Blossom names the blob. */
    val hash: String,
    /**
     * The name to upload under. The hex hash, never the real filename: a name
     * like `bank-statement.pdf` describes the file to a server that is
     * supposed to see opaque bytes.
     */
    val baseFileName: String,
    /**
     * Always [OPAQUE]. The real type is in the sealed `imeta` descriptor;
     * declaring `image/jpeg` here would tell the host what kind of file this
     * is for no benefit to anybody.
     */
    val contentType: String,
    /**
     * Always null. Alt text is plaintext on a Blossom upload, and an alt
     * string describing a private photo *is* the photo's caption, handed to
     * the one party that was supposed to see nothing.
     */
    val alt: String?,
    /** Always null, for the same reason as [alt]. */
    val sensitiveContent: String?,
    /**
     * Always false — `/upload`, never `/media`. The `/media` endpoint asks the
     * server to re-encode, and re-encoding ciphertext destroys it. An account
     * with "optimize uploads" turned on would otherwise break every
     * attachment, and only the recipient would find out.
     */
    val useMediaEndpoint: Boolean,
) {
    val length: Long get() = bytes.size.toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CordnBlobUpload) return false
        return bytes.contentEquals(other.bytes) &&
            hash == other.hash &&
            baseFileName == other.baseFileName &&
            contentType == other.contentType &&
            alt == other.alt &&
            sensitiveContent == other.sensitiveContent &&
            useMediaEndpoint == other.useMediaEndpoint
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + hash.hashCode()

    companion object {
        /** What the blob host is told the type is. */
        const val OPAQUE = "application/octet-stream"

        fun of(media: CordnEncryptedMedia): CordnBlobUpload {
            val hash = sha256(media.ciphertext).toHexKey()
            return CordnBlobUpload(
                bytes = media.ciphertext,
                hash = hash,
                baseFileName = hash,
                contentType = OPAQUE,
                alt = null,
                sensitiveContent = null,
                useMediaEndpoint = false,
            )
        }
    }
}
