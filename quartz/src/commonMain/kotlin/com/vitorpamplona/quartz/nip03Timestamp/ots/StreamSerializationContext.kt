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

class StreamSerializationContext {
    val buffer: MutableList<Byte> = ArrayList<Byte>()

    val output: ByteArray
        get() {
            val bytes = ByteArray(this.buffer.size)

            for (i in this.buffer.indices) {
                bytes[i] = this.buffer.get(i)
            }

            return bytes
        }

    fun writeBool(value: Boolean) {
        if (value) {
            this.writeByte(0xff.toByte())
        } else {
            this.writeByte(0x00.toByte())
        }
    }

    fun writeVaruint(value: Int) {
        var value = value
        if ((value) == 0) {
            this.writeByte(0x00.toByte())
        } else {
            while (value != 0) {
                var b = ((value and 0xff) and 127).toByte()

                if ((value) > 127) {
                    b = (b.toInt() or 128).toByte()
                }

                this.writeByte(b)

                if ((value) <= 127) {
                    break
                }

                value = value shr 7
            }
        }
    }

    fun writeByte(value: Byte) {
        this.buffer.add(value)
    }

    fun writeBytes(value: ByteArray) {
        for (b in value) {
            this.writeByte(b)
        }
    }

    fun writeVarbytes(value: ByteArray) {
        this.writeVaruint(value.size)
        this.writeBytes(value)
    }

    override fun toString(): String = this.output.contentToString()
}
