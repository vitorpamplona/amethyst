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

import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.Op
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpCrypto
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.NoSuchAlgorithmException

/**
 * Class representing Detached com.vitorpamplona.quartz.ots.Timestamp File.
 * A file containing a timestamp for another file.
 * Contains a timestamp, along with a header and the digest of the file.
 */
class DetachedTimestampFile(
    val fileHashOp: Op,
    val timestamp: Timestamp,
) {
    /**
     * The digest of the file that was timestamped.
     *
     * @return The message inside the timestamp.
     */
    fun fileDigest(): ByteArray? = this.timestamp.digest

    /**
     * Serialize a com.vitorpamplona.quartz.ots.Timestamp File.
     *
     * @param ctx The stream serialization context.
     */
    fun serialize(ctx: StreamSerializationContext) {
        ctx.writeBytes(HEADER_MAGIC)
        ctx.writeVaruint(MAJOR_VERSION.toInt())
        this.fileHashOp.serialize(ctx)
        ctx.writeBytes(this.timestamp.digest)
        this.timestamp.serialize(ctx)
    }

    /**
     * Serialize a com.vitorpamplona.quartz.ots.Timestamp File.
     *
     * @return The byte array of serialized data.
     */
    fun serialize(): ByteArray {
        val ctx = StreamSerializationContext()
        this.serialize(ctx)

        return ctx.output
    }

    /**
     * Print the object.
     *
     * @return The output.
     */
    override fun toString(): String {
        var output = "com.vitorpamplona.quartz.ots.DetachedTimestampFile\n"
        output += "fileHashOp: " + this.fileHashOp.toString() + '\n'
        output += "timestamp: " + this.timestamp.toString() + '\n'

        return output
    }

    companion object {
        /**
         * Header magic bytes Designed to be give the user some information in a hexdump, while being
         * identified as 'data' by the file utility.
         *
         * @default \x00OpenTimestamps\x00\x00Proof\x00\xbf\x89\xe2\xe8\x84\xe8\x92\x94
         */
        val HEADER_MAGIC: ByteArray =
            byteArrayOf(
                0x00.toByte(),
                0x4f.toByte(),
                0x70.toByte(),
                0x65.toByte(),
                0x6e.toByte(),
                0x54.toByte(),
                0x69.toByte(),
                0x6d.toByte(),
                0x65.toByte(),
                0x73.toByte(),
                0x74.toByte(),
                0x61.toByte(),
                0x6d.toByte(),
                0x70.toByte(),
                0x73.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x50.toByte(),
                0x72.toByte(),
                0x6f.toByte(),
                0x6f.toByte(),
                0x66.toByte(),
                0x00.toByte(),
                0xbf.toByte(),
                0x89.toByte(),
                0xe2.toByte(),
                0xe8.toByte(),
                0x84.toByte(),
                0xe8.toByte(),
                0x92.toByte(),
                0x94.toByte(),
            )

        /**
         * While the git commit timestamps have a minor version, probably better to
         * leave it out here: unlike Git commits round-tripping is an issue when
         * timestamps are upgraded, and we could end up with bugs related to not
         * saving/updating minor version numbers correctly.
         *
         * @default 1
         */
        const val MAJOR_VERSION: Byte = 1

        /**
         * Deserialize a com.vitorpamplona.quartz.ots.Timestamp File.
         *
         * @param ctx The stream deserialization context.
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         */
        @Throws(DeserializationException::class)
        fun deserialize(ctx: StreamDeserializationContext): DetachedTimestampFile {
            ctx.assertMagic(HEADER_MAGIC)
            ctx.readVaruint()

            val fileHashOp = Op.deserialize(ctx) as OpCrypto
            val fileHash = ctx.readBytes(fileHashOp.digestLength())
            val timestamp = Timestamp.deserialize(ctx, fileHash)

            ctx.assertEof()

            return DetachedTimestampFile(fileHashOp, timestamp)
        }

        /**
         * Deserialize a com.vitorpamplona.quartz.ots.Timestamp File.
         *
         * @param ots The byte array of deserialization DetachedFileTimestamped.
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         */
        @Throws(DeserializationException::class)
        fun deserialize(ots: ByteArray): DetachedTimestampFile {
            val ctx = StreamDeserializationContext(ots)

            return deserialize(ctx)
        }

        /**
         * Read the Detached com.vitorpamplona.quartz.ots.Timestamp File from bytes.
         *
         * @param fileHashOp The file hash operation.
         * @param ctx        The stream deserialization context.
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(NoSuchAlgorithmException::class)
        fun from(
            fileHashOp: OpCrypto,
            ctx: StreamDeserializationContext,
        ): DetachedTimestampFile {
            val fdHash = fileHashOp.hashFd(ctx)

            return DetachedTimestampFile(fileHashOp, Timestamp(fdHash))
        }

        /**
         * Read the Detached com.vitorpamplona.quartz.ots.Timestamp File from bytes.
         *
         * @param fileHashOp The file hash operation.
         * @param bytes      The byte array of data to hash
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(Exception::class)
        fun from(
            fileHashOp: OpCrypto,
            bytes: ByteArray,
        ): DetachedTimestampFile {
            val fdHash = fileHashOp.hashFd(bytes)

            return DetachedTimestampFile(fileHashOp, Timestamp(fdHash))
        }

        /**
         * Read the Detached com.vitorpamplona.quartz.ots.Timestamp File from hash.
         *
         * @param inputStream The InputStream of the file to hash
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         * @throws Exception if the input stream is null
         */
        @Throws(Exception::class)
        fun from(inputStream: InputStream): DetachedTimestampFile {
            try {
                val fileTimestamp: DetachedTimestampFile =
                    from(OpSHA256(), inputStream) // Read from file reader stream
                return fileTimestamp
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                throw Exception()
            }
        }

        /**
         * Read the Detached com.vitorpamplona.quartz.ots.Timestamp File from InputStream.
         *
         * @param fileHashOp  The file hash operation.
         * @param inputStream The input stream file.
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         * @throws IOException              desc
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(IOException::class, NoSuchAlgorithmException::class)
        fun from(
            fileHashOp: OpCrypto,
            inputStream: InputStream,
        ): DetachedTimestampFile {
            val fdHash = fileHashOp.hashFd(inputStream)

            return DetachedTimestampFile(fileHashOp, Timestamp(fdHash))
        }

        /**
         * Read the Detached com.vitorpamplona.quartz.ots.Timestamp File from hash.
         *
         * @param hash The hash of the file.
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         */
        fun from(hash: Hash): DetachedTimestampFile = DetachedTimestampFile(hash.op, Timestamp(hash.value))

        /**
         * Read the Detached com.vitorpamplona.quartz.ots.Timestamp File from File.
         *
         * @param fileHashOp The file hash operation.
         * @param file       The hash file.
         * @return The generated com.vitorpamplona.quartz.ots.DetachedTimestampFile object.
         * @throws IOException              desc
         * @throws NoSuchAlgorithmException desc
         */
        @Throws(IOException::class, NoSuchAlgorithmException::class)
        fun from(
            fileHashOp: OpCrypto,
            file: File?,
        ): DetachedTimestampFile {
            val fdHash = fileHashOp.hashFd(file)

            return DetachedTimestampFile(fileHashOp, Timestamp(fdHash))
        }
    }
}
