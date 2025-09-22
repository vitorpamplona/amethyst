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
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamSerializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Utils.compare
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.utils.Hex.encode
import com.vitorpamplona.quartz.utils.Log

/**
 * Operations that act on a message and a single argument.
 *
 * @see OpUnary
 */
abstract class OpBinary(
    val arg: ByteArray = byteArrayOf(),
) : Op(),
    Comparable<Op> {
    public override fun tagName(): String = ""

    public override fun serialize(ctx: StreamSerializationContext) {
        super.serialize(ctx)
        ctx.writeVarbytes(this.arg)
    }

    override fun toString(): String = this.tagName() + ' ' + encode(this.arg).lowercase()

    override fun compareTo(other: Op): Int {
        if (other is OpBinary && this.tag() == other.tag()) {
            return compare(this.arg, other.arg)
        }

        return this.tag() - other.tag()
    }

    override fun hashCode(): Int = TAG.toInt() xor this.arg.contentHashCode()

    companion object {
        @Throws(DeserializationException::class)
        fun deserializeFromTag(
            ctx: StreamDeserializationContext,
            tag: Byte,
        ): Op? {
            val arg = ctx.readVarbytes(MAX_RESULT_LENGTH, 1)

            if (tag == OpAppend.TAG) {
                return OpAppend(arg)
            } else if (tag == OpPrepend.TAG) {
                return OpPrepend(arg)
            } else {
                Log.e(
                    "OpenTimestamp",
                    "Unknown operation tag: " + tag + " 0x" + tag.toHexString(),
                )
                return null // TODO: Is this OK? Won't it blow up later? Better to throw?
            }
        }
    }
}
