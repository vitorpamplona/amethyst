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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

/**
 * BOLT-1 BigSize codec: a variable-length unsigned integer, big-endian, with a
 * length prefix byte (`0xfd`/`0xfe`/`0xff`) for the 2/4/8-byte forms. Used for
 * both TLV record types and lengths.
 *
 * See https://github.com/lightning/bolts/blob/master/01-messaging.md#appendix-a-bigsize-test-vectors
 */
object BigSize {
    fun encodedSize(value: Long): Int =
        when {
            value < 0xfdL -> 1
            value < 0x10000L -> 3
            value < 0x100000000L -> 5
            else -> 9
        }

    fun encode(value: Long): ByteArray {
        require(value >= 0) { "BigSize cannot encode a negative value" }
        return when {
            value < 0xfdL -> byteArrayOf(value.toByte())
            value < 0x10000L -> byteArrayOf(0xfd.toByte(), (value shr 8).toByte(), value.toByte())
            value < 0x100000000L ->
                byteArrayOf(
                    0xfe.toByte(),
                    (value shr 24).toByte(),
                    (value shr 16).toByte(),
                    (value shr 8).toByte(),
                    value.toByte(),
                )
            else ->
                byteArrayOf(
                    0xff.toByte(),
                    (value shr 56).toByte(),
                    (value shr 48).toByte(),
                    (value shr 40).toByte(),
                    (value shr 32).toByte(),
                    (value shr 24).toByte(),
                    (value shr 16).toByte(),
                    (value shr 8).toByte(),
                    value.toByte(),
                )
        }
    }
}

/**
 * A cursor over a byte array for reading BigSize values and fixed-length byte
 * runs out of a TLV stream.
 */
class TlvReader(
    private val bytes: ByteArray,
) {
    var pos: Int = 0
        private set

    fun remaining(): Int = bytes.size - pos

    fun readBytes(n: Int): ByteArray {
        require(n >= 0 && n <= remaining()) { "TLV read of $n bytes exceeds the remaining ${remaining()}" }
        val out = bytes.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    private fun readByte(): Int {
        require(remaining() > 0) { "Unexpected end of TLV stream" }
        return bytes[pos++].toInt() and 0xff
    }

    fun readBigSize(): Long {
        val first = readByte()
        // BOLT-1 requires minimal encoding. Rejecting non-minimal forms also rejects
        // the multi-byte forms whose value would overflow a signed Long (≥ 2^63 reads
        // back negative, failing the `>=` bound), so callers get a non-negative Long.
        return when (first) {
            0xff -> readUInt(8).also { require(it >= 0x100000000L) { "non-minimal or out-of-range BigSize" } }
            0xfe -> readUInt(4).also { require(it >= 0x10000L) { "non-minimal BigSize" } }
            0xfd -> readUInt(2).also { require(it >= 0xfdL) { "non-minimal BigSize" } }
            else -> first.toLong()
        }
    }

    private fun readUInt(n: Int): Long {
        var value = 0L
        repeat(n) {
            value = (value shl 8) or readByte().toLong()
        }
        return value
    }
}

/**
 * A single TLV record: an unsigned [type], its [value] bytes, and a canonical
 * `type || length || value` [encoded] form (used both to re-serialize a stream
 * and as the leaf input for the BOLT12 signature merkle tree).
 */
class TlvRecord(
    val type: Long,
    val value: ByteArray,
) {
    val encoded: ByteArray by lazy {
        BigSize.encode(type) + BigSize.encode(value.size.toLong()) + value
    }

    /** BOLT12 signature TLV elements (types 240..1000 inclusive) are excluded from the merkle root. */
    fun isSignatureElement() = type in SIGNATURE_TYPE_MIN..SIGNATURE_TYPE_MAX

    companion object {
        const val SIGNATURE_TYPE_MIN = 240L
        const val SIGNATURE_TYPE_MAX = 1000L
    }
}

/**
 * A parsed BOLT12 TLV stream (an offer, invoice request, invoice, or payer
 * proof). Records are kept in the order read; BOLT12 requires strictly
 * ascending, unique types, which [read] enforces.
 */
class TlvStream(
    val records: List<TlvRecord>,
) {
    fun get(type: Long): TlvRecord? = records.firstOrNull { it.type == type }

    fun value(type: Long): ByteArray? = get(type)?.value

    fun has(type: Long): Boolean = get(type) != null

    /** The truncated-uint64 value of a record, or null if absent. */
    fun tu64(type: Long): Long? = value(type)?.let { Bolt12Values.tu64(it) }

    fun encode(): ByteArray {
        var size = 0
        for (r in records) size += r.encoded.size
        val out = ByteArray(size)
        var offset = 0
        for (r in records) {
            r.encoded.copyInto(out, offset)
            offset += r.encoded.size
        }
        return out
    }

    companion object {
        fun read(bytes: ByteArray): TlvStream {
            val reader = TlvReader(bytes)
            val records = ArrayList<TlvRecord>()
            var lastType = -1L
            while (reader.remaining() > 0) {
                val type = reader.readBigSize()
                val length = reader.readBigSize()
                require(length in 0..reader.remaining().toLong()) { "TLV length $length out of range (remaining ${reader.remaining()})" }
                val value = reader.readBytes(length.toInt())
                require(type > lastType) { "TLV records must be strictly ascending (saw $type after $lastType)" }
                lastType = type
                records.add(TlvRecord(type, value))
            }
            return TlvStream(records)
        }

        fun readOrNull(bytes: ByteArray): TlvStream? =
            try {
                read(bytes)
            } catch (_: Exception) {
                null
            }
    }
}

/** Encoders/decoders for the BOLT12 fundamental TLV value types we use. */
object Bolt12Values {
    /**
     * Decodes a `tu64` (truncated uint64): a big-endian unsigned integer with
     * leading zero bytes removed, so its encoded length is 0..8 bytes.
     */
    fun tu64(bytes: ByteArray): Long {
        require(bytes.size <= 8) { "tu64 must be at most 8 bytes, was ${bytes.size}" }
        var value = 0L
        for (b in bytes) {
            value = (value shl 8) or (b.toLong() and 0xff)
        }
        return value
    }

    /** Minimal big-endian `tu64` encoding of [value] (leading zero bytes stripped). */
    fun tu64ToBytes(value: Long): ByteArray {
        require(value >= 0) { "tu64 cannot encode a negative value" }
        if (value == 0L) return ByteArray(0)
        val full =
            byteArrayOf(
                (value shr 56).toByte(),
                (value shr 48).toByte(),
                (value shr 40).toByte(),
                (value shr 32).toByte(),
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte(),
            )
        var start = 0
        while (start < full.size && full[start].toInt() == 0) start++
        return full.copyOfRange(start, full.size)
    }
}
