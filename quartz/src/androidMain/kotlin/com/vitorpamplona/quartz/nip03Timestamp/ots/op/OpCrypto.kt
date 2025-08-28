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

import android.util.Log
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

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
        try {
            val digest = MessageDigest.getInstance(this.hashLibName())
            val hash = digest.digest(msg)

            return hash
        } catch (e: NoSuchAlgorithmException) {
            Log.e("OpenTimestamp", "NoSuchAlgorithmException")
            e.printStackTrace()

            return byteArrayOf() // TODO: Is this OK? Won't it blow up later? Better to throw?
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun hashFd(ctx: StreamDeserializationContext): ByteArray {
        val digest = MessageDigest.getInstance(this.hashLibName())
        var chunk = ctx.read(1048576)

        while (chunk != null && chunk.size > 0) {
            digest.update(chunk)
            chunk = ctx.read(1048576)
        }

        val hash = digest.digest()

        return hash
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun hashFd(file: File?): ByteArray = hashFd(FileInputStream(file))

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun hashFd(bytes: ByteArray): ByteArray {
        val ctx = StreamDeserializationContext(bytes)

        return hashFd(ctx)
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun hashFd(inputStream: InputStream): ByteArray {
        val digest = MessageDigest.getInstance(this.hashLibName())
        val chunk = ByteArray(1048576)
        var count = inputStream.read(chunk, 0, 1048576)

        while (count >= 0) {
            digest.update(chunk, 0, count)
            count = inputStream.read(chunk, 0, 1048576)
        }

        inputStream.close()
        val hash = digest.digest()

        return hash
    }

    companion object {
        @JvmStatic
        fun deserializeFromTag(
            ctx: StreamDeserializationContext,
            tag: Byte,
        ): Op? = OpUnary.deserializeFromTag(ctx, tag)
    }
}
