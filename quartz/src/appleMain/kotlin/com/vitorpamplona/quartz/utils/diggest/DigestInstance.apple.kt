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
package com.vitorpamplona.quartz.utils.diggest

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA1_CTX
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1_Final
import platform.CoreCrypto.CC_SHA1_Init
import platform.CoreCrypto.CC_SHA1_Update
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

/** SHA-1 and SHA-256 on CommonCrypto (hardware-accelerated); RIPEMD-160 in Kotlin. */
actual class DigestInstance actual constructor(
    algorithm: String,
) {
    private val digest: StreamingDigest =
        when (algorithm) {
            "SHA-256" -> CommonCryptoSha256()
            "SHA-1" -> CommonCryptoSha1()
            else -> kotlinStreamingDigest(algorithm)
        }

    actual fun update(array: ByteArray) = digest.update(array)

    actual fun update(byte: Byte) = digest.update(byte)

    actual fun digest(): ByteArray = digest.digest()

    actual fun digest(input: ByteArray): ByteArray {
        digest.update(input)
        return digest.digest()
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
private class CommonCryptoSha256 : StreamingDigest {
    // The context lives on the native heap; the cleaner frees it with this object.
    private val context: CPointer<CC_SHA256_CTX> = nativeHeap.alloc<CC_SHA256_CTX>().ptr

    @Suppress("unused")
    private val cleaner: Cleaner = createCleaner(context) { nativeHeap.free(it) }

    init {
        CC_SHA256_Init(context)
    }

    override fun update(array: ByteArray) {
        if (array.isEmpty()) return
        array.usePinned { CC_SHA256_Update(context, it.addressOf(0), array.size.convert()) }
    }

    override fun update(byte: Byte) = update(byteArrayOf(byte))

    override fun digest(): ByteArray {
        val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
        out.usePinned { CC_SHA256_Final(it.addressOf(0), context) }
        CC_SHA256_Init(context)
        return out.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
private class CommonCryptoSha1 : StreamingDigest {
    private val context: CPointer<CC_SHA1_CTX> = nativeHeap.alloc<CC_SHA1_CTX>().ptr

    @Suppress("unused")
    private val cleaner: Cleaner = createCleaner(context) { nativeHeap.free(it) }

    init {
        CC_SHA1_Init(context)
    }

    override fun update(array: ByteArray) {
        if (array.isEmpty()) return
        array.usePinned { CC_SHA1_Update(context, it.addressOf(0), array.size.convert()) }
    }

    override fun update(byte: Byte) = update(byteArrayOf(byte))

    override fun digest(): ByteArray {
        val out = UByteArray(CC_SHA1_DIGEST_LENGTH)
        out.usePinned { CC_SHA1_Final(it.addressOf(0), context) }
        CC_SHA1_Init(context)
        return out.toByteArray()
    }
}
