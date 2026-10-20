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
package com.vitorpamplona.quartz.utils.ciphers

import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCOperation
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar

/** AES-CBC with PKCS#7 padding on CommonCrypto's CCCrypt (hardware AES). */
actual class AESCBC actual constructor(
    actual val keyBytes: ByteArray,
    actual val iv: ByteArray,
) : NostrCipher {
    actual override fun name(): String = "aes-cbc"

    actual override fun encrypt(bytesToEncrypt: ByteArray): ByteArray = crypt(kCCEncrypt, bytesToEncrypt)

    actual override fun decrypt(bytesToDecrypt: ByteArray): ByteArray {
        require(bytesToDecrypt.isNotEmpty() && bytesToDecrypt.size % 16 == 0) {
            "AES-CBC ciphertext must be a non-empty multiple of 16 bytes, was ${bytesToDecrypt.size}"
        }
        return crypt(kCCDecrypt, bytesToDecrypt)
    }

    actual override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? =
        try {
            decrypt(bytesToDecrypt)
        } catch (e: IllegalArgumentException) {
            Log.w("AESCBC", "Failed to decrypt", e)
            null
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun crypt(
        operation: CCOperation,
        input: ByteArray,
    ): ByteArray {
        require(keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32) {
            "AES key must be 16, 24 or 32 bytes, was ${keyBytes.size}"
        }
        // CCCrypt reads 16 IV bytes whatever the array holds.
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes, was ${iv.size}" }
        // Padding adds at most one block.
        val out = ByteArray(input.size + 16)
        val written =
            memScoped {
                val moved = alloc<size_tVar>()
                val status =
                    CCCrypt(
                        operation,
                        kCCAlgorithmAES.convert(),
                        kCCOptionPKCS7Padding.convert(),
                        keyBytes.refTo(0),
                        keyBytes.size.convert(),
                        iv.refTo(0),
                        if (input.isEmpty()) null else input.refTo(0),
                        input.size.convert(),
                        out.refTo(0),
                        out.size.convert(),
                        moved.ptr,
                    )
                require(status == kCCSuccess) { "AES-CBC failed: CCCrypt status $status" }
                moved.value.toInt()
            }
        return out.copyOf(written)
    }
}
