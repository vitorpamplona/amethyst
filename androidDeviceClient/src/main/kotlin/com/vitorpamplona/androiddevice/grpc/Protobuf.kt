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
package com.vitorpamplona.androiddevice.grpc

import java.io.ByteArrayOutputStream

/**
 * Just enough of the protobuf wire format for the emulator's few messages: varints, length-
 * delimited fields (strings, bytes, nested messages, packed floats) and fixed 32/64-bit values.
 * Writing skips a field at its default value, as proto3 does.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun bytes(): ByteArray = out.toByteArray()

    private fun varint(value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) {
            out.write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun tag(
        field: Int,
        wireType: Int,
    ) = varint(((field shl 3) or wireType).toLong())

    /** An int32/int64/uint32/uint64/enum/bool field. A negative int32 takes ten bytes, as protobuf writes it. */
    fun int(
        field: Int,
        value: Long,
    ): ProtoWriter {
        if (value == 0L) return this
        tag(field, VARINT)
        varint(value)
        return this
    }

    fun int(
        field: Int,
        value: Int,
    ) = int(field, value.toLong())

    fun bool(
        field: Int,
        value: Boolean,
    ) = int(field, if (value) 1L else 0L)

    fun string(
        field: Int,
        value: String?,
    ): ProtoWriter {
        if (value.isNullOrEmpty()) return this
        return bytes(field, value.toByteArray(Charsets.UTF_8))
    }

    fun bytes(
        field: Int,
        value: ByteArray,
    ): ProtoWriter {
        tag(field, LENGTH)
        varint(value.size.toLong())
        out.write(value)
        return this
    }

    /** A nested message, written even when empty (its presence can matter). */
    fun message(
        field: Int,
        build: ProtoWriter.() -> Unit,
    ) = bytes(field, ProtoWriter().apply(build).bytes())

    /** A `double`: fixed64, little-endian, as protobuf lays it out. */
    fun double(
        field: Int,
        value: Double,
    ): ProtoWriter {
        if (value == 0.0) return this
        tag(field, FIXED64)
        val bits = value.toRawBits()
        repeat(8) { i -> out.write(((bits ushr (8 * i)) and 0xff).toInt()) }
        return this
    }

    fun packedFloats(
        field: Int,
        values: FloatArray,
    ): ProtoWriter {
        if (values.isEmpty()) return this
        val packed = ByteArrayOutputStream()
        values.forEach { f ->
            val bits = f.toRawBits()
            repeat(4) { i -> packed.write((bits ushr (8 * i)) and 0xff) }
        }
        return bytes(field, packed.toByteArray())
    }

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH = 2
        const val FIXED32 = 5
    }
}

/** One field read off the wire: its number, and the value as the wire type carries it. */
sealed class ProtoField(
    val number: Int,
) {
    class Varint(
        number: Int,
        val value: Long,
    ) : ProtoField(number)

    class Length(
        number: Int,
        val value: ByteArray,
    ) : ProtoField(number) {
        val string: String get() = value.toString(Charsets.UTF_8)

        fun message(): List<ProtoField> = ProtoReader.read(value)
    }

    class Fixed64(
        number: Int,
        val value: Long,
    ) : ProtoField(number)

    class Fixed32(
        number: Int,
        val value: Int,
    ) : ProtoField(number)
}

object ProtoReader {
    /** Every field of the message [bytes], in order. Throws [IllegalArgumentException] on a malformed message. */
    fun read(bytes: ByteArray): List<ProtoField> {
        val fields = ArrayList<ProtoField>()
        var at = 0

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(at < bytes.size) { "Truncated varint" }
                val b = bytes[at++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "Varint too long" }
            }
        }

        fun fixed(size: Int): Long {
            require(at + size <= bytes.size) { "Truncated fixed field" }
            var v = 0L
            for (i in 0 until size) v = v or ((bytes[at + i].toLong() and 0xff) shl (8 * i))
            at += size
            return v
        }

        while (at < bytes.size) {
            val key = varint()
            val number = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                ProtoWriter.VARINT -> fields += ProtoField.Varint(number, varint())
                ProtoWriter.FIXED64 -> fields += ProtoField.Fixed64(number, fixed(8))
                ProtoWriter.LENGTH -> {
                    val length = varint().toInt()
                    require(length >= 0 && at + length <= bytes.size) { "Truncated field $number" }
                    fields += ProtoField.Length(number, bytes.copyOfRange(at, at + length))
                    at += length
                }
                ProtoWriter.FIXED32 -> fields += ProtoField.Fixed32(number, fixed(4).toInt())
                else -> throw IllegalArgumentException("Unsupported wire type in field $number")
            }
        }
        return fields
    }
}

fun List<ProtoField>.varint(number: Int): Long = (lastOrNull { it.number == number } as? ProtoField.Varint)?.value ?: 0L

/** A `double` field, or 0 when it isn't there (proto3's default). */
fun List<ProtoField>.double(number: Int): Double = (lastOrNull { it.number == number } as? ProtoField.Fixed64)?.value?.let { Double.fromBits(it) } ?: 0.0

fun List<ProtoField>.string(number: Int): String = (lastOrNull { it.number == number } as? ProtoField.Length)?.string ?: ""

fun List<ProtoField>.bytes(number: Int): ByteArray = (lastOrNull { it.number == number } as? ProtoField.Length)?.value ?: ByteArray(0)

fun List<ProtoField>.message(number: Int): List<ProtoField> = (lastOrNull { it.number == number } as? ProtoField.Length)?.message() ?: emptyList()

fun List<ProtoField>.messages(number: Int): List<List<ProtoField>> = filter { it.number == number }.mapNotNull { (it as? ProtoField.Length)?.message() }
