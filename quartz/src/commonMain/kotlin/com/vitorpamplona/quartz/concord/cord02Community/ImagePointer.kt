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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.Serializable

/**
 * A CORD-02 §6 **encrypted-media** pointer (community/channel icon or banner). The media host stores
 * only ciphertext; the per-image AES-256-GCM [key] + [nonce] ride inside the member-sealed Control
 * Plane metadata, and [hash] is the SHA-256 of the *plaintext* so a swapped blob fails closed.
 *
 * Wire shape is pinned to the Concord v2 reference client (`concord-v2/lib/types.ts`): an object,
 * not a URL string — a member fetches [url], AES-256-GCM-decrypts with [key]/[nonce], then verifies
 * the plaintext SHA-256 equals [hash] before displaying.
 */
@Serializable
data class ImagePointer(
    val url: String = "",
    /** Hex AES-256-GCM key (32 bytes). */
    val key: String = "",
    /** Hex AES-GCM nonce / IV (16 bytes). */
    val nonce: String = "",
    /** Hex SHA-256 of the plaintext, for integrity. */
    val hash: String = "",
) {
    /** True once every field needed to fetch + decrypt is present. */
    fun isResolvable(): Boolean = url.isNotBlank() && key.isNotBlank() && nonce.isNotBlank() && hash.isNotBlank()

    /**
     * Decrypt the fetched [ciphertext] blob (AES-256-GCM under [key]/[nonce], CORD-02 §6) and verify
     * the plaintext SHA-256 against [hash]. Returns the plaintext image bytes, or null if decryption or
     * the integrity check fails — a swapped or corrupt blob fails closed rather than rendering garbage.
     */
    fun decryptOrNull(ciphertext: ByteArray): ByteArray? {
        val plaintext = AESGCM(key.hexToByteArray(), nonce.hexToByteArray()).decryptOrNull(ciphertext) ?: return null
        if (sha256(plaintext).toHexKey() != hash.lowercase()) return null
        return plaintext
    }
}
