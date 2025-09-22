/**
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
package com.vitorpamplona.quartz.nip03Timestamp.ots.op

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.RIPEMD160Digest

/**
 * Cryptographic RIPEMD160 operation.
 * Cryptographic operation tag numbers taken from RFC4880, although it's not
 * guaranteed that they'll continue to match that RFC in the future.
 *
 * @see OpCrypto
 */
class OpRIPEMD160 : OpCrypto() {
    override fun tag(): Byte = TAG

    override fun tagName(): String = "ripemd160"

    override fun hashLibName(): String = "ripemd160"

    override fun digestLength(): Int = 20

    override fun call(msg: ByteArray): ByteArray {
        val digest = RIPEMD160Digest()
        digest.update(msg, 0, msg.size)
        val hash = ByteArray(digest.getDigestSize())
        digest.doFinal(hash, 0)

        return hash
    }

    override fun equals(other: Any?): Boolean = (other is OpRIPEMD160)

    override fun hashCode(): Int = TAG.toInt()

    companion object {
        val TAG: Byte = 0x03

        fun deserializeFromTag(
            ctx: StreamDeserializationContext,
            tag: Byte,
        ): Op? = OpCrypto.deserializeFromTag(ctx, tag)
    }
}
