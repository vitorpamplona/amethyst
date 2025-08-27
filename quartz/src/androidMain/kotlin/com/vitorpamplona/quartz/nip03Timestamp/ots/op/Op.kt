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
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException

/**
 * Operations are the edges in the timestamp tree, with each operation taking a message and zero or more arguments to produce a result.
 */
abstract class Op : Comparable<Op> {
    open fun tagName(): String = ""

    open fun tag(): Byte = TAG

    /**
     * Serialize operation.
     *
     * @param ctx The stream serialization context.
     */
    open fun serialize(ctx: StreamSerializationContext) {
        if (this.tag().toInt() == 0x00) {
            Log.e("OpenTimestamp", "No valid serialized Op")
            // TODO: Is it OK to just log and carry on? Won't it blow up later? Better to throw?
        }

        ctx.writeByte(this.tag())
    }

    /**
     * Apply the operation to a message.
     * Raises MsgValueError if the message value is invalid, such as it being
     * too long, or it causing the result to be too long.
     *
     * @param msg The message.
     * @return the msg after the operation has been applied
     */
    open fun call(msg: ByteArray): ByteArray {
        if (msg.size > MAX_MSG_LENGTH) {
            Log.e("OpenTimestamp", "Error : Message too long;")
            return byteArrayOf() // TODO: Is this OK? Won't it blow up later? Better to throw?
        }

        val r = this.call(msg)

        if (r.size > MAX_RESULT_LENGTH) {
            Log.e("OpenTimestamp", "Error : Result too long;")
            // TODO: Is it OK to just log and carry on? Won't it blow up later? Better to throw?
        }

        return r
    }

    override fun compareTo(other: Op): Int = this.tag() - other.tag()

    companion object {
        /**
         * Maximum length of an com.vitorpamplona.quartz.ots.op.Op result
         *
         *
         * For a verifier, this limit is what limits the maximum amount of memory you
         * need at any one time to verify a particular timestamp path; while verifying
         * a particular commitment operation path previously calculated results can be
         * discarded.
         *
         *
         * Of course, if everything was a merkle tree you never need to append/prepend
         * anything near 4KiB of data; 64 bytes would be plenty even with SHA512. The
         * main need for this is compatibility with existing systems like Bitcoin
         * timestamps and Certificate Transparency servers. While the pathological
         * limits required by both are quite large - 1MB and 16MiB respectively - 4KiB
         * is perfectly adequate in both cases for more reasonable usage.
         *
         *
         * @see Op subclasses should set this limit even lower if doing so is appropriate
         * for them.
         */
        val MAX_RESULT_LENGTH: Int = 4096

        /**
         * Maximum length of the message an com.vitorpamplona.quartz.ots.op.Op can be applied too.
         *
         *
         * Similar to the result length limit, this limit gives implementations a sane
         * constraint to work with; the maximum result-length limit implicitly
         * constrains maximum message length anyway.
         *
         *
         * com.vitorpamplona.quartz.ots.op.Op subclasses should set this limit even lower if doing so is appropriate
         * for them.
         */
        val MAX_MSG_LENGTH: Int = 4096

        val TAG: Byte = 0x00.toByte()

        /**
         * Deserialize operation from a buffer.
         *
         * @param ctx The stream deserialization context.
         * @return The subclass Operation.
         */
        @Throws(DeserializationException::class)
        fun deserialize(ctx: StreamDeserializationContext): Op? {
            val tag = ctx.readBytes(1)[0]

            return deserializeFromTag(ctx, tag)
        }

        /**
         * Deserialize operation from a buffer.
         *
         * @param ctx The stream deserialization context.
         * @param tag The tag of the operation.
         * @return The subclass Operation.
         */
        @Throws(DeserializationException::class)
        fun deserializeFromTag(
            ctx: StreamDeserializationContext,
            tag: Byte,
        ): Op? {
            if (tag == OpAppend.TAG) {
                return OpAppend.deserializeFromTag(ctx, tag)
            } else if (tag == OpPrepend.TAG) {
                return OpPrepend.deserializeFromTag(ctx, tag)
            } else if (tag == OpSHA1.TAG) {
                return OpSHA1.deserializeFromTag(ctx, tag)
            } else if (tag == OpSHA256.TAG) {
                return OpSHA256.deserializeFromTag(ctx, tag)
            } else if (tag == OpRIPEMD160.TAG) {
                return OpRIPEMD160.deserializeFromTag(ctx, tag)
            } else if (tag == OpKECCAK256.TAG) {
                return OpKECCAK256.deserializeFromTag(ctx, tag)
            } else {
                Log.e(
                    "OpenTimestamp",
                    "Unknown operation tag: " + tag + " 0x" + String.format("%02x", tag),
                )
                return null // TODO: Is this OK? Won't it blow up later? Better to throw?
            }
        }
    }
}
