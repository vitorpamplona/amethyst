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
import com.vitorpamplona.quartz.utils.diggest.DigestInstance

/**
 * Cryptographic transformations.
 * These transformations have the unique property that for any length message,
 * the size of the result they return is fixed. Additionally, they're the only
 * type of operation that can be applied directly to a stream.
 *
 * @see OpUnary
 */
abstract class OpCrypto internal constructor() : OpUnary() {
    abstract fun hashLibName(): String

    abstract fun digestLength(): Int

    override fun call(msg: ByteArray): ByteArray {
        // For Sha1 & Sha256 use java.security.MessageDigest library
        val digest = DigestInstance(this.hashLibName())
        val hash = digest.digest(msg)

        return hash
    }

    fun hashFd(ctx: StreamDeserializationContext): ByteArray {
        val digest = DigestInstance(this.hashLibName())
        var chunk = ctx.read(1048576)

        while (chunk.isNotEmpty()) {
            digest.update(chunk)
            chunk = ctx.read(1048576)
        }

        val hash = digest.digest()

        return hash
    }

    fun hashFd(bytes: ByteArray): ByteArray {
        val ctx = StreamDeserializationContext(bytes)

        return hashFd(ctx)
    }

    companion object {
        fun deserializeFromTag(
            ctx: StreamDeserializationContext,
            tag: Byte,
        ): Op? = OpUnary.deserializeFromTag(ctx, tag)
    }
}
