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

class StreamDeserializationContext(
    var output: ByteArray,
) {
    var counter: Int = 0

    fun read(l: Int): ByteArray {
        var l = l
        if (this.counter == this.output.size) {
            return byteArrayOf()
        }

        if (l + this.counter > this.output.size) {
            l = this.output.size - this.counter
        }

        // const uint8Array = new Uint8Array(this.buffer,this.counter,l);
        val uint8Array = this.output.copyOfRange(this.counter, this.counter + l)
        this.counter += l

        return uint8Array
    }

    fun readBool(): Boolean {
        val b = this.read(1)[0]

        if (b.toInt() == 0xff) {
            return true
        } else if (b.toInt() == 0x00) {
            return false
        }

        return false
    }

    fun readVaruint(): Int {
        var value = 0
        var shift: Byte = 0
        var b: Byte

        do {
            b = this.read(1)[0]
            value = value or ((b.toInt() and 127) shl shift.toInt())
            shift = (shift + 7).toByte()
        } while ((b.toInt() and 128) != 0)

        return value
    }

    @Throws(DeserializationException::class)
    fun readBytes(expectedLength: Int): ByteArray {
        if (expectedLength == 0) {
            return this.readVarbytes(1024, 0)
        }

        return this.read(expectedLength)
    }

    @Throws(DeserializationException::class)
    fun readVarbytes(
        maxLen: Int,
        minLen: Int = 0,
    ): ByteArray {
        val l = this.readVaruint()

        if (l > maxLen) {
            throw DeserializationException("varbytes max length exceeded;")
        } else if (l < minLen) {
            throw DeserializationException("varbytes min length not met;")
        }

        return this.read(l)
    }

    fun assertMagic(expectedMagic: ByteArray): Boolean {
        val actualMagic = this.read(expectedMagic.size)

        return expectedMagic.contentEquals(actualMagic)
    }

    fun assertEof(): Boolean {
        val excess = this.read(1)
        return excess.isNotEmpty()
    }

    override fun toString(): String = this.output.contentToString()
}
