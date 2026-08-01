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
package com.vitorpamplona.quartz.utils

/**
 * Fast, allocation-conscious hex codec used throughout Quartz for keys, event
 * ids and signatures. Backed by pre-computed lookup tables and benchmarked
 * against the secp256k1 codec and Kotlin's stdlib `HexFormat` (see
 * `benchmark/.../HexBenchmark.kt`).
 *
 * Most call sites should prefer the extension functions in
 * [com.vitorpamplona.quartz.nip01Core.core] — `ByteArray.toHexKey()` and
 * `HexKey.hexToByteArray()` — which delegate here. Reach for this object
 * directly when you want to validate without decoding ([isHex] / [isHex64]) or
 * compare a hex string to raw bytes without allocating ([isEqual]).
 *
 * ```kotlin
 * val hex = Hex.encode(bytes) // ByteArray -> lower-case hex
 * val bytes = Hex.decode(hex) // hex (any case) -> ByteArray
 * if (Hex.isHex64(id)) { ... } // is this a valid 32-byte hex id?
 * val id = Hex.decode64(idHex) // exactly 64 chars or it throws
 * val sig = Hex.decode128OrNull(sigHex) // exactly 128 chars or null
 * ```
 */
object Hex {
    private const val LOWER_CASE_HEX = "0123456789abcdef"
    private const val UPPER_CASE_HEX = "0123456789ABCDEF"

    private val hexToByte: IntArray =
        IntArray(256) { -1 }.apply {
            LOWER_CASE_HEX.forEachIndexed { index, char -> this[char.code] = index }
            UPPER_CASE_HEX.forEachIndexed { index, char -> this[char.code] = index }
        }

    // Encodes both chars in a single Int variable
    private val byteToHex =
        IntArray(256) {
            (LOWER_CASE_HEX[(it shr 4)].code shl 8) or LOWER_CASE_HEX[(it and 0xF)].code
        }

    /**
     * True when [hex] is a non-null, even-length string of only hex digits
     * (upper or lower case). Rejects odd lengths and stray non-hex chars (e.g.
     * emoji in `p` tags) instead of throwing. ~47ns in debug on the Emulator;
     * use [isHex64] when the length is known to be 64.
     */
    fun isHex(hex: String?): Boolean {
        if (hex == null) return false
        if (hex.length and 1 != 0) return false

        return try {
            internalIsHex(hex, hexToByte)
        } catch (_: IllegalArgumentException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        } catch (_: IndexOutOfBoundsException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        }
    }

    // breaking this function away from the main one improves performance for some reason
    fun internalIsHex(
        hex: String,
        hexToByte: IntArray,
    ): Boolean {
        for (c in hex.indices) {
            if (hexToByte[hex[c].code] < 0) return false
        }
        return true
    }

    /**
     * Validates the first 64 chars of [hex] as hex digits — the fast path for
     * checking a 32-byte pubkey or event id. ~30% faster than [isHex] because
     * the length is fixed and the checks are unrolled. Assumes [hex] is at least
     * 64 chars long; it does not verify the total length.
     */
    fun isHex64(hex: String): Boolean =
        try {
            hexToByte[hex[0].code] >= 0 &&
                hexToByte[hex[1].code] >= 0 &&
                hexToByte[hex[2].code] >= 0 &&
                hexToByte[hex[3].code] >= 0 &&
                hexToByte[hex[4].code] >= 0 &&
                hexToByte[hex[5].code] >= 0 &&
                hexToByte[hex[6].code] >= 0 &&
                hexToByte[hex[7].code] >= 0 &&
                hexToByte[hex[8].code] >= 0 &&
                hexToByte[hex[9].code] >= 0 &&

                hexToByte[hex[10].code] >= 0 &&
                hexToByte[hex[11].code] >= 0 &&
                hexToByte[hex[12].code] >= 0 &&
                hexToByte[hex[13].code] >= 0 &&
                hexToByte[hex[14].code] >= 0 &&
                hexToByte[hex[15].code] >= 0 &&
                hexToByte[hex[16].code] >= 0 &&
                hexToByte[hex[17].code] >= 0 &&
                hexToByte[hex[18].code] >= 0 &&
                hexToByte[hex[19].code] >= 0 &&

                hexToByte[hex[20].code] >= 0 &&
                hexToByte[hex[21].code] >= 0 &&
                hexToByte[hex[22].code] >= 0 &&
                hexToByte[hex[23].code] >= 0 &&
                hexToByte[hex[24].code] >= 0 &&
                hexToByte[hex[25].code] >= 0 &&
                hexToByte[hex[26].code] >= 0 &&
                hexToByte[hex[27].code] >= 0 &&
                hexToByte[hex[28].code] >= 0 &&
                hexToByte[hex[29].code] >= 0 &&

                hexToByte[hex[30].code] >= 0 &&
                hexToByte[hex[31].code] >= 0 &&
                hexToByte[hex[32].code] >= 0 &&
                hexToByte[hex[33].code] >= 0 &&
                hexToByte[hex[34].code] >= 0 &&
                hexToByte[hex[35].code] >= 0 &&
                hexToByte[hex[36].code] >= 0 &&
                hexToByte[hex[37].code] >= 0 &&
                hexToByte[hex[38].code] >= 0 &&
                hexToByte[hex[39].code] >= 0 &&

                hexToByte[hex[40].code] >= 0 &&
                hexToByte[hex[41].code] >= 0 &&
                hexToByte[hex[42].code] >= 0 &&
                hexToByte[hex[43].code] >= 0 &&
                hexToByte[hex[44].code] >= 0 &&
                hexToByte[hex[45].code] >= 0 &&
                hexToByte[hex[46].code] >= 0 &&
                hexToByte[hex[47].code] >= 0 &&
                hexToByte[hex[48].code] >= 0 &&
                hexToByte[hex[49].code] >= 0 &&

                hexToByte[hex[50].code] >= 0 &&
                hexToByte[hex[51].code] >= 0 &&
                hexToByte[hex[52].code] >= 0 &&
                hexToByte[hex[53].code] >= 0 &&
                hexToByte[hex[54].code] >= 0 &&
                hexToByte[hex[55].code] >= 0 &&
                hexToByte[hex[56].code] >= 0 &&
                hexToByte[hex[57].code] >= 0 &&
                hexToByte[hex[58].code] >= 0 &&
                hexToByte[hex[59].code] >= 0 &&

                hexToByte[hex[60].code] >= 0 &&
                hexToByte[hex[61].code] >= 0 &&
                hexToByte[hex[62].code] >= 0 &&
                hexToByte[hex[63].code] >= 0
        } catch (_: IllegalArgumentException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        } catch (_: IndexOutOfBoundsException) {
            // there are p tags with emoji's which makes the hex[c].code > 256
            false
        }

    /**
     * Decodes [hex] (upper or lower case) into bytes. Requires an even length —
     * throws [IllegalArgumentException] otherwise. Does not itself validate the
     * characters, so guard untrusted input with [isHex] first (or use
     * `HexKey.hexToByteArrayOrNull()`).
     */
    fun decode(hex: String): ByteArray {
        // faster version of hex decoder
        require(hex.length and 1 == 0) {
            "Invalid hex $hex"
        }
        // table hoisted into a local: the JVM/ART doesn't reliably prove the
        // field load loop-invariant, and re-loading it per char costs ~25%
        val table = hexToByte
        val out = ByteArray(hex.length shr 1)
        var c = 0
        for (i in out.indices) {
            out[i] = ((table[hex[c++].code] shl 4) or table[hex[c++].code]).toByte()
        }
        return out
    }

    /**
     * Decodes a 32-byte pubkey/event id, accepting only exactly 64 hex chars
     * (upper or lower case). Throws [IllegalArgumentException] on any other
     * length or on non-hex characters — use [decode64OrNull] for untrusted
     * input. Single pass: validation is folded into the decode, so this is
     * faster than `isHex64` + [decode].
     */
    fun decode64(hex: String): ByteArray = decode64OrNull(hex) ?: throw IllegalArgumentException("Invalid 64-char hex $hex")

    /** Like [decode64] but returns null instead of throwing. */
    fun decode64OrNull(hex: String): ByteArray? = if (hex.length == 64) decodeExactOrNull(hex, 32) else null

    /**
     * Decodes a 64-byte value (a Schnorr signature), accepting only exactly
     * 128 hex chars (upper or lower case). Throws [IllegalArgumentException]
     * on any other length or on non-hex characters — use [decode128OrNull]
     * for untrusted input.
     */
    fun decode128(hex: String): ByteArray = decode128OrNull(hex) ?: throw IllegalArgumentException("Invalid 128-char hex $hex")

    /** Like [decode128] but returns null instead of throwing. */
    fun decode128OrNull(hex: String): ByteArray? = if (hex.length == 128) decodeExactOrNull(hex, 64) else null

    /**
     * Decodes [hex] into [byteLen] bytes, or null if any char is not a hex
     * digit. The caller has already checked `hex.length == 2 * byteLen`.
     *
     * Tuned at the bytecode level (see `HexBenchmark`): the table is hoisted
     * into a local (the JVM/ART can't always prove the field load loop
     * invariant), `inline` turns [byteLen] into a compile-time trip count at
     * each call site, and validation is branchless — the table yields -1 for
     * invalid chars and `255 - code` goes negative for chars above 0xFF (e.g.
     * emoji, kept in bounds by the `and 0xFF` mask), so OR-ing everything into
     * one accumulator and sign-checking it at the end rejects all bad input
     * with no branches and no exception table. ~25% faster than the same loop
     * with a per-iteration field load and a try/catch guard, and ~2x faster
     * than `isHex64` + [decode].
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun decodeExactOrNull(
        hex: String,
        byteLen: Int,
    ): ByteArray? {
        val table = hexToByte
        val out = ByteArray(byteLen)
        var acc = 0
        var c = 0
        for (i in 0 until byteLen) {
            val c0 = hex[c++].code
            val c1 = hex[c++].code
            val b = (table[c0 and 0xFF] shl 4) or table[c1 and 0xFF]
            acc = acc or b or (255 - c0) or (255 - c1)
            out[i] = b.toByte()
        }
        return if (acc < 0) null else out
    }

    /**
     * Encodes a 32-byte pubkey/event id as a 64-char lower-case hex string.
     * Throws [IllegalArgumentException] when [input] is not exactly 32 bytes.
     */
    fun encode64(input: ByteArray): String {
        require(input.size == 32) { "Expected 32 bytes, got ${input.size}" }
        return encode(input)
    }

    /**
     * Encodes a 64-byte value (a Schnorr signature) as a 128-char lower-case
     * hex string. Throws [IllegalArgumentException] when [input] is not
     * exactly 64 bytes.
     */
    fun encode128(input: ByteArray): String {
        require(input.size == 64) { "Expected 64 bytes, got ${input.size}" }
        return encode(input)
    }

    /** Encodes [input] as a lower-case hex string (two chars per byte). */
    fun encode(input: ByteArray): String {
        val table = byteToHex
        val out = CharArray(input.size * 2)
        var outIdx = 0
        for (i in 0 until input.size) {
            val chars = table[input[i].toInt() and 0xFF]
            out[outIdx++] = (chars shr 8).toChar()
            out[outIdx++] = (chars and 0xFF).toChar()
        }
        return out.concatToString()
    }

    /**
     * Packs 16 hex chars starting at [offset] into a single [Long] (big-endian:
     * the first char becomes the most-significant nibble). No allocations, no
     * branches — just 16 table lookups and shifts.
     *
     * Assumes [hex] has at least `offset + 16` valid hex chars; it does not
     * validate. Guard untrusted input with [isHex64] first, otherwise an invalid
     * char (table value `-1`) corrupts the result.
     */
    fun readLong(
        hex: String,
        offset: Int,
    ): Long {
        // table hoisted into a local — one field load instead of sixteen
        val t = hexToByte
        return (t[hex[offset].code].toLong() shl 60) or
            (t[hex[offset + 1].code].toLong() shl 56) or
            (t[hex[offset + 2].code].toLong() shl 52) or
            (t[hex[offset + 3].code].toLong() shl 48) or
            (t[hex[offset + 4].code].toLong() shl 44) or
            (t[hex[offset + 5].code].toLong() shl 40) or
            (t[hex[offset + 6].code].toLong() shl 36) or
            (t[hex[offset + 7].code].toLong() shl 32) or
            (t[hex[offset + 8].code].toLong() shl 28) or
            (t[hex[offset + 9].code].toLong() shl 24) or
            (t[hex[offset + 10].code].toLong() shl 20) or
            (t[hex[offset + 11].code].toLong() shl 16) or
            (t[hex[offset + 12].code].toLong() shl 12) or
            (t[hex[offset + 13].code].toLong() shl 8) or
            (t[hex[offset + 14].code].toLong() shl 4) or
            t[hex[offset + 15].code].toLong()
    }

    /**
     * Reads the first 64 bits (16 hex chars) of [hex] as a single [Long].
     * Ideal as a cheap, collision-resistant map/set key or bucket hash for a
     * 32-byte event id or pubkey. Assumes [hex] is at least 16 valid hex chars —
     * see [readLong].
     */
    fun toLong64(hex: String): Long = readLong(hex, 0)

    /**
     * Reads the first 128 bits (32 hex chars) of [hex] as two [Long]s, most
     * significant first. Assumes [hex] is at least 32 valid hex chars — see
     * [readLong].
     */
    fun toLong128(hex: String): LongArray =
        longArrayOf(
            readLong(hex, 0),
            readLong(hex, 16),
        )

    /**
     * Reads a full 256-bit (64 hex char) id/pubkey/signature-half as four
     * [Long]s, most significant first. Assumes [hex] is at least 64 valid hex
     * chars — see [readLong].
     */
    fun toLong256(hex: String): LongArray =
        longArrayOf(
            readLong(hex, 0),
            readLong(hex, 16),
            readLong(hex, 32),
            readLong(hex, 48),
        )

    /**
     * True when the hex string [id] encodes exactly the bytes [ourId], compared
     * without allocating a decode buffer. Handy for matching an incoming hex id
     * against bytes you already hold. Assumes [id] is at least `2 * ourId.size`
     * chars and lower-case (as produced by [encode]).
     */
    fun isEqual(
        id: String,
        ourId: ByteArray,
    ): Boolean {
        val table = byteToHex
        var charIndex = 0
        for (i in 0 until ourId.size) {
            val chars = table[ourId[i].toInt() and 0xFF]
            if (
                id[charIndex++] != (chars shr 8).toChar() ||
                id[charIndex++] != (chars and 0xFF).toChar()
            ) {
                return false
            }
        }
        return true
    }
}
