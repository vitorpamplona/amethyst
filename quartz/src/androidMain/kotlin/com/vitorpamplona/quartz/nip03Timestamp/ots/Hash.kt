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
package com.vitorpamplona.quartz.nip03Timestamp.ots

import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpCrypto
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpKECCAK256
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpRIPEMD160
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA1
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256
import com.vitorpamplona.quartz.utils.Hex.encode
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.NoSuchAlgorithmException

/**
 * Create a Hash object.
 *
 * @param value     - The byte array of the hash
 * @param algorithm - The hashlib tag of crypto operation
 */
class Hash(
    val value: ByteArray,
    val algorithm: Byte,
) {
    /**
     * Create a Hash object.
     *
     * @param value - The byte array of the hash
     * @param label - The hashlib name of crypto operation
     */
    constructor(value: ByteArray, label: String) : this(value, getOp(label).tag())

    val op: OpCrypto =
        when (this.algorithm) {
            OpSHA1.TAG -> OpSHA1()
            OpSHA256.TAG -> OpSHA256()
            OpRIPEMD160.TAG -> OpRIPEMD160()
            OpKECCAK256.TAG -> OpKECCAK256()
            else -> OpSHA256()
        }

    /**
     * Print the object.
     *
     * @return The output.
     */
    override fun toString(): String {
        var output = "com.vitorpamplona.quartz.ots.Hash\n"
        output += "algorithm: " + this.op.hashLibName() + '\n'
        output += "value: " + encode(this.value) + '\n'

        return output
    }

    companion object {
        /**
         * Get Crypto operation from hashlib tag.
         *
         * @param algorithm The hashlib tag.
         * @return The generated com.vitorpamplona.quartz.ots.OpCrypto object.
         */
        fun getOp(algorithm: Byte): OpCrypto =
            when (algorithm) {
                OpSHA1.TAG -> OpSHA1()
                OpSHA256.TAG -> OpSHA256()
                OpRIPEMD160.TAG -> OpRIPEMD160()
                OpKECCAK256.TAG -> OpKECCAK256()
                else -> OpSHA256()
            }

        /**
         * Get Crypto operation from hashlib name.
         *
         * @param label The hashlib name.
         * @return The generated com.vitorpamplona.quartz.ots.OpCrypto object.
         */
        fun getOp(label: String): OpCrypto {
            if (label.lowercase() == OpSHA1().tagName()) {
                return OpSHA1()
            } else if (label.lowercase() == OpSHA256().tagName()) {
                return OpSHA256()
            } else if (label.lowercase() == OpRIPEMD160().tagName()) {
                return OpRIPEMD160()
            } else if (label.lowercase() == OpKECCAK256().tagName()) {
                return OpKECCAK256()
            }

            return OpSHA256()
        }

        /**
         * Build hash from data.
         *
         * @param bytes     The byte array of data to hash.
         * @param algorithm The hash file.
         * @return The generated com.vitorpamplona.quartz.ots.Hash object.
         * @throws IOException              desc
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(IOException::class, NoSuchAlgorithmException::class)
        fun from(
            bytes: ByteArray,
            algorithm: Byte,
        ): Hash {
            val opCrypto: OpCrypto = getOp(algorithm)
            val value = opCrypto.hashFd(bytes)

            return Hash(value, algorithm)
        }

        /**
         * Build hash from File.
         *
         * @param file      The File of data to hash.
         * @param algorithm The hash file.
         * @return The generated com.vitorpamplona.quartz.ots.Hash object.
         * @throws IOException              desc
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(IOException::class, NoSuchAlgorithmException::class)
        fun from(
            file: File?,
            algorithm: Byte,
        ): Hash {
            val opCrypto: OpCrypto = getOp(algorithm)
            val value = opCrypto.hashFd(file)

            return Hash(value, algorithm)
        }

        /**
         * Build hash from InputStream.
         *
         * @param inputStream The InputStream of data to hash.
         * @param algorithm   The hash file.
         * @return The generated com.vitorpamplona.quartz.ots.Hash object.
         * @throws IOException              desc
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(IOException::class, NoSuchAlgorithmException::class)
        fun from(
            inputStream: InputStream,
            algorithm: Byte,
        ): Hash {
            val opCrypto: OpCrypto = getOp(algorithm)
            val value = opCrypto.hashFd(inputStream)

            return Hash(value, algorithm)
        }
    }
}
