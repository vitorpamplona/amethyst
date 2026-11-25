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
package com.vitorpamplona.emulator.proto

import java.io.ByteArrayOutputStream

/** Protobuf wire types (https://protobuf.dev/programming-guides/encoding/). */
internal object WireType {
    const val VARINT = 0
    const val FIXED64 = 1
    const val LENGTH_DELIMITED = 2
    const val START_GROUP = 3
    const val END_GROUP = 4
    const val FIXED32 = 5
}

/** Raised when bytes on the wire are not a valid protobuf message. */
class ProtobufException(
    message: String,
) : RuntimeException(message)

/**
 * A minimal proto3 encoder: just the scalar kinds the emulator messages use. Fields that hold
 * their type's default (0, false, "", empty) are left out, as proto3 does.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(
        field: Int,
        wireType: Int,
    ) = rawVarint(((field shl 3) or wireType).toLong())

    fun rawVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun rawFixed32(value: Int) {
        for (i in 0 until 4) out.write((value ushr (8 * i)) and 0xFF)
    }

    private fun rawFixed64(value: Long) {
        for (i in 0 until 8) out.write(((value ushr (8 * i)) and 0xFF).toInt())
    }

    /** int32 and enums: negative values are sign-extended to ten bytes, as the spec requires. */
    fun int32(
        field: Int,
        value: Int,
    ) {
        if (value == 0) return
        tag(field, WireType.VARINT)
        rawVarint(value.toLong())
    }

    fun enum(
        field: Int,
        value: Int,
    ) = int32(field, value)

    fun uint32(
        field: Int,
        value: Int,
    ) {
        if (value == 0) return
        tag(field, WireType.VARINT)
        rawVarint(value.toLong() and 0xFFFFFFFFL)
    }

    fun int64(
        field: Int,
        value: Long,
    ) {
        if (value == 0L) return
        tag(field, WireType.VARINT)
        rawVarint(value)
    }

    fun uint64(
        field: Int,
        value: Long,
    ) = int64(field, value)

    fun bool(
        field: Int,
        value: Boolean,
    ) {
        if (!value) return
        tag(field, WireType.VARINT)
        rawVarint(1)
    }

    fun double(
        field: Int,
        value: Double,
    ) {
        if (value == 0.0 && 1.0 / value > 0) return
        tag(field, WireType.FIXED64)
        rawFixed64(value.toRawBits())
    }

    fun string(
        field: Int,
        value: String?,
    ) {
        if (value.isNullOrEmpty()) return
        bytes(field, value.encodeToByteArray())
    }

    fun bytes(
        field: Int,
        value: ByteArray?,
    ) {
        if (value == null || value.isEmpty()) return
        tag(field, WireType.LENGTH_DELIMITED)
        rawVarint(value.size.toLong())
        out.write(value)
    }

    /** A packed `repeated float`, as `ParameterValue.data` declares it. */
    fun packedFloats(
        field: Int,
        values: List<Float>,
    ) {
        if (values.isEmpty()) return
        tag(field, WireType.LENGTH_DELIMITED)
        rawVarint(values.size * 4L)
        values.forEach { rawFixed32(it.toRawBits()) }
    }

    /** An embedded message. Written even when it encodes to nothing, so presence is kept. */
    fun message(
        field: Int,
        body: ProtoWriter.() -> Unit,
    ) {
        val bytes = ProtoWriter().apply(body).toByteArray()
        tag(field, WireType.LENGTH_DELIMITED)
        rawVarint(bytes.size.toLong())
        out.write(bytes)
    }
}

internal inline fun protoMessage(body: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(body).toByteArray()

/**
 * A minimal protobuf decoder over one message's bytes. Call [nextField] until it returns false,
 * then read the current field with the reader that matches its declared type, or [skip] it.
 * Unknown fields must be skipped, which keeps the client working when the emulator adds fields.
 */
internal class ProtoReader(
    private val buf: ByteArray,
    private var pos: Int = 0,
    private val end: Int = buf.size,
) {
    var field: Int = 0
        private set
    var wireType: Int = 0
        private set

    fun nextField(): Boolean {
        if (pos >= end) return false
        val key = readRawVarint()
        field = (key ushr 3).toInt()
        wireType = (key and 7).toInt()
        if (field == 0) throw ProtobufException("field number 0 at offset $pos")
        return true
    }

    fun readRawVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (pos >= end) throw ProtobufException("truncated varint")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw ProtobufException("varint longer than 10 bytes")
    }

    private fun rawFixed32(): Int {
        require(4)
        var v = 0
        for (i in 0 until 4) v = v or ((buf[pos++].toInt() and 0xFF) shl (8 * i))
        return v
    }

    private fun rawFixed64(): Long {
        require(8)
        var v = 0L
        for (i in 0 until 8) v = v or ((buf[pos++].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun require(n: Int) {
        if (n < 0 || end - pos < n) throw ProtobufException("truncated field: need $n bytes, have ${end - pos}")
    }

    private fun expect(type: Int) {
        if (wireType != type) throw ProtobufException("field $field: wire type $wireType, expected $type")
    }

    fun readInt32(): Int {
        expect(WireType.VARINT)
        return readRawVarint().toInt()
    }

    fun readEnum(): Int = readInt32()

    /** uint32 values above [Int.MAX_VALUE] come back negative; mask with 0xFFFFFFFF if needed. */
    fun readUInt32(): Int = readInt32()

    fun readInt64(): Long {
        expect(WireType.VARINT)
        return readRawVarint()
    }

    fun readUInt64(): Long = readInt64()

    fun readBool(): Boolean = readInt64() != 0L

    fun readDouble(): Double {
        expect(WireType.FIXED64)
        return Double.fromBits(rawFixed64())
    }

    fun readFloat(): Float {
        expect(WireType.FIXED32)
        return Float.fromBits(rawFixed32())
    }

    fun readBytes(): ByteArray {
        expect(WireType.LENGTH_DELIMITED)
        val len = lengthPrefix()
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readString(): String = readBytes().decodeToString()

    /** A reader over the embedded message in the current field, without copying it. */
    fun readMessage(): ProtoReader {
        expect(WireType.LENGTH_DELIMITED)
        val len = lengthPrefix()
        val sub = ProtoReader(buf, pos, pos + len)
        pos += len
        return sub
    }

    /** A `repeated float`, packed or not (parsers must accept both). */
    fun readFloats(into: MutableList<Float>) {
        if (wireType == WireType.FIXED32) {
            into.add(readFloat())
            return
        }
        expect(WireType.LENGTH_DELIMITED)
        val len = lengthPrefix()
        if (len % 4 != 0) throw ProtobufException("packed floats of $len bytes")
        val stop = pos + len
        while (pos < stop) into.add(Float.fromBits(rawFixed32()))
    }

    private fun lengthPrefix(): Int {
        val len = readRawVarint()
        if (len < 0 || len > Int.MAX_VALUE) throw ProtobufException("bad length $len")
        require(len.toInt())
        return len.toInt()
    }

    fun skip() {
        when (wireType) {
            WireType.VARINT -> readRawVarint()
            WireType.FIXED64 -> {
                require(8)
                pos += 8
            }
            WireType.LENGTH_DELIMITED -> {
                // Not `pos += lengthPrefix()`: that reads pos before the prefix moves it.
                val len = lengthPrefix()
                pos += len
            }
            WireType.FIXED32 -> {
                require(4)
                pos += 4
            }
            WireType.START_GROUP -> {
                val group = field
                while (true) {
                    if (!nextField()) throw ProtobufException("unterminated group $group")
                    if (wireType == WireType.END_GROUP) {
                        if (field != group) throw ProtobufException("group $group closed by $field")
                        break
                    }
                    skip()
                }
            }
            else -> throw ProtobufException("field $field: unknown wire type $wireType")
        }
    }
}

/** Runs [onField] for every field; fields it does not handle must be skipped by calling `skip()`. */
internal inline fun ByteArray.forEachProtoField(onField: ProtoReader.(field: Int) -> Unit) = ProtoReader(this).forEachField(onField)

internal inline fun ProtoReader.forEachField(onField: ProtoReader.(field: Int) -> Unit) {
    while (nextField()) onField(field)
}
