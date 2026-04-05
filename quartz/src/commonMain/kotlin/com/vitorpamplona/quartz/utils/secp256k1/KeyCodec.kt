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
package com.vitorpamplona.quartz.utils.secp256k1

// =====================================================================================
// KEY ENCODING, DECODING, AND COORDINATE CONVERSION FOR secp256k1
// =====================================================================================
//
// Converts between public key formats and handles the math for decompressing
// compressed keys (recovering y from x via square root on the curve y² = x³ + 7).
//
// Formats:
//   - x-only (32 bytes): BIP-340 convention, even y assumed
//   - Compressed (33 bytes): 02/03 prefix indicates y parity, followed by x
//   - Uncompressed (65 bytes): 04 prefix, followed by x and y
//   - Jacobian (X, Y, Z): internal projective representation, needs inversion to convert
// =====================================================================================

/**
 * Public key encoding, decoding, and coordinate conversion for secp256k1.
 */
internal object KeyCodec {
    /** Curve constant b = 7 in y² = x³ + 7. */
    private val B = intArrayOf(7, 0, 0, 0, 0, 0, 0, 0)

    /**
     * Lift an x-coordinate to a curve point with even y (BIP-340 convention).
     * Computes y = √(x³ + 7) mod p. Returns false if x is not a valid coordinate.
     */
    fun liftX(
        outX: IntArray,
        outY: IntArray,
        x: IntArray,
    ): Boolean {
        if (U256.cmp(x, FieldP.P) >= 0) return false
        val t = IntArray(8)
        FieldP.sqr(t, x)
        FieldP.mul(t, t, x)
        FieldP.add(t, t, B) // t = x³ + 7
        if (!FieldP.sqrt(outY, t)) return false
        U256.copyInto(outX, x)
        if (outY[0] and 1 != 0) FieldP.neg(outY, outY) // Ensure even y
        return true
    }

    /** Check if y-coordinate is even (LSB = 0). */
    fun hasEvenY(y: IntArray): Boolean = y[0] and 1 == 0

    /**
     * Parse a serialized public key (33 bytes compressed or 65 bytes uncompressed).
     * For compressed keys (02/03 prefix): decompresses y from x via square root.
     * For uncompressed keys (04 prefix): validates the point is on the curve.
     */
    fun parsePublicKey(
        pubkey: ByteArray,
        outX: IntArray,
        outY: IntArray,
    ): Boolean =
        when {
            pubkey.size == 33 && (pubkey[0] == 0x02.toByte() || pubkey[0] == 0x03.toByte()) -> {
                val x = U256.fromBytes(pubkey.copyOfRange(1, 33))
                if (U256.cmp(x, FieldP.P) >= 0) return false
                val t = IntArray(8)
                FieldP.sqr(t, x)
                FieldP.mul(t, t, x)
                FieldP.add(t, t, B) // y² = x³ + 7
                if (!FieldP.sqrt(outY, t)) return false
                U256.copyInto(outX, x)
                val isOdd = outY[0] and 1 == 1
                if (isOdd != (pubkey[0] == 0x03.toByte())) FieldP.neg(outY, outY)
                true
            }

            pubkey.size == 65 && pubkey[0] == 0x04.toByte() -> {
                val x = U256.fromBytes(pubkey.copyOfRange(1, 33))
                val y = U256.fromBytes(pubkey.copyOfRange(33, 65))
                val y2 = IntArray(8)
                val x3p7 = IntArray(8)
                val t = IntArray(8)
                FieldP.sqr(y2, y)
                FieldP.sqr(t, x)
                FieldP.mul(x3p7, t, x)
                FieldP.add(x3p7, x3p7, B)
                if (U256.cmp(y2, x3p7) != 0) return false
                U256.copyInto(outX, x)
                U256.copyInto(outY, y)
                true
            }

            else -> {
                false
            }
        }

    /** Serialize as 65-byte uncompressed: 04 || x (32 bytes) || y (32 bytes). */
    fun serializeUncompressed(
        x: IntArray,
        y: IntArray,
    ): ByteArray {
        val r = ByteArray(65)
        r[0] = 0x04
        U256.toBytesInto(x, r, 1)
        U256.toBytesInto(y, r, 33)
        return r
    }

    /** Serialize as 33-byte compressed: 02/03 || x (32 bytes). */
    fun serializeCompressed(
        x: IntArray,
        y: IntArray,
    ): ByteArray {
        val r = ByteArray(33)
        r[0] = if (hasEvenY(y)) 0x02 else 0x03
        U256.toBytesInto(x, r, 1)
        return r
    }
}
