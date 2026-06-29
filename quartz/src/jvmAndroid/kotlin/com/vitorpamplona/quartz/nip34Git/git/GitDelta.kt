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
package com.vitorpamplona.quartz.nip34Git.git

/**
 * Applies a git delta ([delta]) against a [base] object payload, reconstructing
 * the target object. This is the format used by `OBJ_OFS_DELTA` / `OBJ_REF_DELTA`
 * packfile entries.
 *
 * Layout: a base-size varint, a target-size varint, then a stream of
 * instructions — copy (high bit set, offset/size assembled from the selected
 * following bytes) or insert (a literal run of `cmd` bytes).
 */
object GitDelta {
    fun apply(
        base: ByteArray,
        delta: ByteArray,
    ): ByteArray {
        var pos = 0

        val baseSize = readVarInt(delta) { pos }.also { pos = it.second }.first
        require(baseSize == base.size) {
            "delta base size mismatch: header says $baseSize, base is ${base.size}"
        }
        val targetSize = readVarInt(delta) { pos }.also { pos = it.second }.first

        val out = ByteArray(targetSize)
        var outPos = 0

        while (pos < delta.size) {
            val cmd = delta[pos++].toInt() and 0xFF
            if (cmd and 0x80 != 0) {
                // copy from base
                var copyOffset = 0
                var copySize = 0
                if (cmd and 0x01 != 0) copyOffset = copyOffset or (delta[pos++].toInt() and 0xFF)
                if (cmd and 0x02 != 0) copyOffset = copyOffset or ((delta[pos++].toInt() and 0xFF) shl 8)
                if (cmd and 0x04 != 0) copyOffset = copyOffset or ((delta[pos++].toInt() and 0xFF) shl 16)
                if (cmd and 0x08 != 0) copyOffset = copyOffset or ((delta[pos++].toInt() and 0xFF) shl 24)
                if (cmd and 0x10 != 0) copySize = copySize or (delta[pos++].toInt() and 0xFF)
                if (cmd and 0x20 != 0) copySize = copySize or ((delta[pos++].toInt() and 0xFF) shl 8)
                if (cmd and 0x40 != 0) copySize = copySize or ((delta[pos++].toInt() and 0xFF) shl 16)
                if (copySize == 0) copySize = 0x10000
                base.copyInto(out, outPos, copyOffset, copyOffset + copySize)
                outPos += copySize
            } else if (cmd != 0) {
                // insert literal: the next `cmd` bytes
                delta.copyInto(out, outPos, pos, pos + cmd)
                outPos += cmd
                pos += cmd
            } else {
                throw IllegalArgumentException("invalid delta opcode 0x00")
            }
        }

        require(outPos == targetSize) { "delta produced $outPos bytes, expected $targetSize" }
        return out
    }

    /** Little-endian base-128 varint as used in delta size headers. */
    private inline fun readVarInt(
        data: ByteArray,
        startPos: () -> Int,
    ): Pair<Int, Int> {
        var pos = startPos()
        var value = 0
        var shift = 0
        while (true) {
            val b = data[pos++].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return value to pos
    }
}
