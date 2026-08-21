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
package com.vitorpamplona.amethyst.commons.model.account.transfer

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip44Encryption.crypto.XChaCha20Poly1305
import com.vitorpamplona.quartz.nip49PrivKeyEnc.SCrypt
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.UnicodeNormalizer

/**
 * Password encryption for the account transfer file.
 *
 * Same construction NIP-49 uses for `ncryptsec` — scrypt over an NFKC-normalized
 * password, then XChaCha20-Poly1305 — but over an arbitrary-length payload
 * instead of a fixed 32-byte key, which is why it can't just call
 * [com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49]. Both primitives are the
 * pure-Kotlin ones already shipping in Quartz, so this adds no dependency.
 *
 * ```
 * offset  size  field
 * 0       8     magic "AMYXFER1"
 * 8       1     format version
 * 9       1     log2(scrypt N)
 * 10      16    scrypt salt
 * 26      24    XChaCha20 nonce
 * 50      ..    ciphertext || 16-byte Poly1305 tag
 * ```
 *
 * The 50-byte header is passed as associated data, so editing the version, the
 * scrypt cost, the salt or the nonce fails authentication instead of silently
 * changing how the file is read. In particular an attacker cannot downgrade
 * [logN] to make a stolen file cheap to crack — the tag is computed over it.
 */
object AccountTransferEnvelope {
    /** Identifies the file and its wire format. Bumped only on a breaking layout change. */
    private val MAGIC = "AMYXFER1".encodeToByteArray()

    const val VERSION: Byte = 1

    /**
     * scrypt cost, as log2(N). 2^16 with r=8 needs ~64 MiB — the NIP-49 default,
     * already what this app's key backup runs, and affordable on a mid-range
     * phone. Raising it hardens a stolen file against offline guessing, but a
     * cost the exporting device can't afford means no backup at all, which is
     * the worse failure.
     */
    const val DEFAULT_LOG_N = 16

    private const val MAGIC_SIZE = 8
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 24
    private const val HEADER_SIZE = MAGIC_SIZE + 1 + 1 + SALT_SIZE + NONCE_SIZE

    /** Thrown for every failure a user can actually cause, with a message the UI can show. */
    class InvalidTransferFile(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    /** Encrypts [bundle] under [password], returning the bytes to write to disk. */
    fun encrypt(
        bundle: AccountTransferBundle,
        password: String,
        logN: Int = DEFAULT_LOG_N,
    ): ByteArray {
        val salt = RandomInstance.bytes(SALT_SIZE)
        val nonce = RandomInstance.bytes(NONCE_SIZE)
        val header = buildHeader(logN.toByte(), salt, nonce)
        val key = deriveKey(password, salt, logN)

        val ciphertext =
            XChaCha20Poly1305.encrypt(
                plaintext = JsonMapper.toJson(bundle).encodeToByteArray(),
                ad = header,
                nonce = nonce,
                key = key,
            )

        return header + ciphertext
    }

    /**
     * Decrypts a transfer file.
     *
     * @throws InvalidTransferFile when the bytes aren't a transfer file, were
     * written by a newer format, don't authenticate (wrong password or tampered
     * content), or don't parse as a bundle once decrypted.
     */
    fun decrypt(
        bytes: ByteArray,
        password: String,
    ): AccountTransferBundle {
        if (bytes.size <= HEADER_SIZE) {
            throw InvalidTransferFile("Not an Amethyst transfer file: too short")
        }
        if (!bytes.copyOfRange(0, MAGIC_SIZE).contentEquals(MAGIC)) {
            throw InvalidTransferFile("Not an Amethyst transfer file")
        }

        val version = bytes[MAGIC_SIZE]
        if (version > VERSION) {
            throw InvalidTransferFile("This file was written by a newer version of Amethyst")
        }

        val logN = bytes[MAGIC_SIZE + 1].toInt()
        // Guard before scrypt runs: a hostile file could otherwise name a cost
        // that allocates far more memory than any phone has.
        if (logN < 1 || logN > 20) {
            throw InvalidTransferFile("Unsupported encryption parameters")
        }

        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val salt = bytes.copyOfRange(MAGIC_SIZE + 2, MAGIC_SIZE + 2 + SALT_SIZE)
        val nonce = bytes.copyOfRange(MAGIC_SIZE + 2 + SALT_SIZE, HEADER_SIZE)
        val key = deriveKey(password, salt, logN)

        val plaintext =
            try {
                XChaCha20Poly1305.decrypt(
                    ciphertextWithTag = bytes.copyOfRange(HEADER_SIZE, bytes.size),
                    ad = header,
                    nonce = nonce,
                    key = key,
                )
            } catch (e: Exception) {
                // Authentication covers the password and every byte of the
                // file, so these are indistinguishable by design. Name the
                // likely cause without claiming the file is intact.
                throw InvalidTransferFile("Wrong password, or the file is damaged", e)
            }

        val bundle =
            try {
                JsonMapper.fromJson<AccountTransferBundle>(plaintext.decodeToString())
            } catch (e: Exception) {
                throw InvalidTransferFile("The file decrypted but its contents are unreadable", e)
            }

        if (bundle.version > AccountTransferBundle.CURRENT_VERSION) {
            throw InvalidTransferFile("This file was written by a newer version of Amethyst")
        }

        return bundle
    }

    private fun buildHeader(
        logN: Byte,
        salt: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        MAGIC.copyInto(header)
        header[MAGIC_SIZE] = VERSION
        header[MAGIC_SIZE + 1] = logN
        salt.copyInto(header, MAGIC_SIZE + 2)
        nonce.copyInto(header, MAGIC_SIZE + 2 + SALT_SIZE)
        return header
    }

    private fun deriveKey(
        password: String,
        salt: ByteArray,
        logN: Int,
    ): ByteArray =
        SCrypt.scrypt(
            // NFKC so a password typed on another keyboard/IME still derives
            // the same key, matching NIP-49.
            passwd = UnicodeNormalizer().normalizeNFKC(password).encodeToByteArray(),
            salt = salt,
            n = 1 shl logN,
            r = 8,
            p = 1,
            dkLen = 32,
        )
}
