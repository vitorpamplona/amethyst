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
 *
 * The digest states after the inner and the outer key block are computed once per
 * key and restored for every message, so a MAC costs only its own blocks plus one
 * outer block, not two extra key blocks.
 */
internal class Hmac(
    private val digest: ShaDigest,
    key: ByteArray,
) {
    private val innerKeyed = digest.newInstance()
    private val outerKeyed = digest.newInstance()
    private val outer = digest.newInstance()
    private val innerHash = ByteArray(digest.digestSize)

    val macSize: Int get() = digest.digestSize

    init {
        rekey(key)
    }

    /** Switches to [key] and drops any message in progress, reusing this instance's buffers. */
    fun rekey(key: ByteArray) {
        val pad = ByteArray(digest.blockSize)
        if (key.size > digest.blockSize) {
            digest.reset()
            digest.update(key)
            digest.digestInto(pad)
        } else {
            key.copyInto(pad)
        }
        for (i in pad.indices) pad[i] = (pad[i].toInt() xor 0x36).toByte()
        innerKeyed.reset()
        innerKeyed.update(pad)
        // 0x36 xor 0x5c turns the inner pad into the outer one.
        for (i in pad.indices) pad[i] = (pad[i].toInt() xor (0x36 xor 0x5c)).toByte()
        outerKeyed.reset()
        outerKeyed.update(pad)
        pad.fill(0)
        digest.copyFrom(innerKeyed)
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
        // Checked before any state changes, so a short buffer leaves the MAC usable.
        require(offset >= 0 && macSize <= out.size - offset) { "no room for a $macSize-byte MAC" }
        digest.digestInto(innerHash)
        outer.copyFrom(outerKeyed)
        outer.update(innerHash)
        outer.digestInto(out, offset)
        innerHash.fill(0)
        digest.copyFrom(innerKeyed)
    }

    fun doFinal(): ByteArray = ByteArray(macSize).also { doFinal(it) }
}
