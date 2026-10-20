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

import com.vitorpamplona.quartz.utils.sha2.ShaDigest

/**
 * HMAC (RFC 2104) over a pure-Kotlin [ShaDigest], for the native [MacInstance].
 * Like JCA's `Mac`, [doFinal] leaves it keyed and ready for the next message.
 */
internal class Hmac(
    private val digest: ShaDigest,
    key: ByteArray,
) {
    private val ipad = ByteArray(digest.blockSize)
    private val opad = ByteArray(digest.blockSize)
    private val innerHash = ByteArray(digest.digestSize)

    val macSize: Int get() = digest.digestSize

    init {
        val blockKey =
            if (key.size > digest.blockSize) {
                digest.update(key)
                digest.digest()
            } else {
                key
            }
        blockKey.copyInto(ipad)
        blockKey.copyInto(opad)
        for (i in ipad.indices) {
            ipad[i] = (ipad[i].toInt() xor 0x36).toByte()
            opad[i] = (opad[i].toInt() xor 0x5c).toByte()
        }
        digest.reset()
        digest.update(ipad)
    }

    fun update(byte: Byte) = digest.update(byte)

    fun update(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
    ) = digest.update(input, offset, length)

    fun doFinal(
        out: ByteArray,
        offset: Int = 0,
    ) {
        digest.digestInto(innerHash)
        digest.update(opad)
        digest.update(innerHash)
        digest.digestInto(out, offset)
        innerHash.fill(0)
        digest.update(ipad)
    }

    fun doFinal(): ByteArray = ByteArray(macSize).also { doFinal(it) }
}
