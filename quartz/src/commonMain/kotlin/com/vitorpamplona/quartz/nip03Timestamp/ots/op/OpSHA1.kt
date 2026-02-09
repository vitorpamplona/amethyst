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
package com.vitorpamplona.quartz.nip03Timestamp.ots.op

import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext

/**
 * Cryptographic SHA1 operation.
 * Cryptographic operation tag numbers taken from RFC4880, although it's not
 * guaranteed that they'll continue to match that RFC in the future.
 * Remember that for timestamping, hash algorithms with collision attacks
 * *are* secure! We've still proven that both messages existed prior to some
 * point in time - the fact that they both have the same hash digest doesn't
 * change that.
 * Heck, even md5 is still secure enough for timestamping... but that's
 * pushing our luck...
 *
 * @see OpCrypto
 */
class OpSHA1 : OpCrypto() {
    public override fun tag(): Byte = TAG

    override fun tagName(): String = "sha1"

    public override fun hashLibName(): String = "SHA-1"

    public override fun digestLength(): Int = 20

    override fun call(msg: ByteArray): ByteArray = super.call(msg)

    override fun equals(other: Any?): Boolean = (other is OpSHA1)

    override fun hashCode(): Int = TAG.toInt()

    companion object {
        val TAG: Byte = 0x02

        fun deserializeFromTag(
            ctx: StreamDeserializationContext,
            tag: Byte,
        ): Op? = OpCrypto.deserializeFromTag(ctx, tag)
    }
}
