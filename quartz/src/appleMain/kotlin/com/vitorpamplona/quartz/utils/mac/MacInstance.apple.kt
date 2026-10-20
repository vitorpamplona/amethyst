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
package com.vitorpamplona.quartz.utils.mac

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmacAlgorithm
import platform.CoreCrypto.CCHmacContext
import platform.CoreCrypto.CCHmacFinal
import platform.CoreCrypto.CCHmacInit
import platform.CoreCrypto.CCHmacUpdate
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCHmacAlgSHA512
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

/** HMAC on CommonCrypto's CCHmac. Like JCA's `Mac`, [doFinal] leaves it keyed for the next message. */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
actual class MacInstance actual constructor(
    algorithm: String,
    key: ByteArray,
) {
    // The context lives on the native heap; the cleaner frees it with this object.
    private val context: CPointer<CCHmacContext> = nativeHeap.alloc<CCHmacContext>().ptr

    @Suppress("unused")
    private val cleaner: Cleaner = createCleaner(context) { nativeHeap.free(it.rawValue) }

    private var ccAlgorithm: CCHmacAlgorithm = ccAlgorithmFor(algorithm)
    private var macLength: Int = macLengthFor(algorithm)

    // CCHmacFinal consumes the keyed state, so the key is kept to re-arm it.
    private var key: ByteArray = key.copyOf()

    init {
        rearm()
    }

    actual fun init(
        key: ByteArray,
        algorithm: String,
    ) {
        ccAlgorithm = ccAlgorithmFor(algorithm)
        macLength = macLengthFor(algorithm)
        this.key = key.copyOf()
        rearm()
    }

    actual fun getMacLength(): Int = macLength

    actual fun update(array: ByteArray) {
        if (array.isEmpty()) return
        array.usePinned { CCHmacUpdate(context, it.addressOf(0), array.size.convert()) }
    }

    actual fun update(byte: Byte) = update(byteArrayOf(byte))

    actual fun doFinal(): ByteArray = ByteArray(macLength).also { doFinal(it, 0) }

    actual fun doFinal(
        output: ByteArray,
        offset: Int,
    ) {
        require(offset >= 0 && macLength <= output.size - offset) { "no room for a $macLength-byte MAC" }
        output.usePinned { CCHmacFinal(context, it.addressOf(offset)) }
        rearm()
    }

    private fun rearm() {
        if (key.isEmpty()) {
            CCHmacInit(context, ccAlgorithm, null, 0.convert())
        } else {
            key.usePinned { CCHmacInit(context, ccAlgorithm, it.addressOf(0), key.size.convert()) }
        }
    }

    private fun ccAlgorithmFor(algorithm: String): CCHmacAlgorithm =
        when (algorithm) {
            "HmacSHA256" -> kCCHmacAlgSHA256
            "HmacSHA512" -> kCCHmacAlgSHA512
            else -> error("Algorithm is not yet supported.")
        }

    private fun macLengthFor(algorithm: String): Int =
        when (algorithm) {
            "HmacSHA256" -> CC_SHA256_DIGEST_LENGTH
            "HmacSHA512" -> CC_SHA512_DIGEST_LENGTH
            else -> error("Algorithm is not yet supported.")
        }
}
