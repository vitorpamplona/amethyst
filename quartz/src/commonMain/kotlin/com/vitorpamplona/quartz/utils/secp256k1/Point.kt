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

/**
 * Elliptic curve point operations on the secp256k1 curve: y² = x³ + 7 (mod p).
 * Uses Jacobian coordinates (X, Y, Z) where affine (x, y) = (X/Z², Y/Z³).
 * The point at infinity is represented by Z = 0.
 */
internal class JPoint(
    val x: IntArray,
    val y: IntArray,
    val z: IntArray,
) {
    companion object {
        val INFINITY = JPoint(IntArray(8), intArrayOf(1, 0, 0, 0, 0, 0, 0, 0), IntArray(8))
    }

    fun isInfinity(): Boolean = U256.isZero(z)
}

internal object ECPoint {
    // Generator point G
    val GX =
        intArrayOf(
            0x16F81798.toInt(),
            0x59F2815B.toInt(),
            0x2DCE28D9.toInt(),
            0x029BFCDB.toInt(),
            0xCE870B07.toInt(),
            0x55A06295.toInt(),
            0xF9DCBBAC.toInt(),
            0x79BE667E.toInt(),
        )
    val GY =
        intArrayOf(
            0xFB10D4B8.toInt(),
            0x9C47D08F.toInt(),
            0xA6855419.toInt(),
            0xFD17B448.toInt(),
            0x0E1108A8.toInt(),
            0x5DA4FBFC.toInt(),
            0x26A3C465.toInt(),
            0x483ADA77.toInt(),
        )

    val G = JPoint(GX.copyOf(), GY.copyOf(), intArrayOf(1, 0, 0, 0, 0, 0, 0, 0))

    // Curve constant b = 7
    private val B = intArrayOf(7, 0, 0, 0, 0, 0, 0, 0)

    /**
     * Point doubling in Jacobian coordinates.
     * Formula from https://hyperelliptic.org/EFD/g1p/auto-shortw-jacobian-0.html#doubling-dbl-2009-l
     */
    fun double(p: JPoint): JPoint {
        if (p.isInfinity()) return JPoint.INFINITY

        val x = p.x
        val y = p.y
        val z = p.z

        val a = FieldP.sqr(x) // A = X1²
        val b = FieldP.sqr(y) // B = Y1²
        val c = FieldP.sqr(b) // C = B²
        val xPlusB = FieldP.add(x, b)
        val d =
            FieldP.sub(
                FieldP.sub(FieldP.sqr(xPlusB), a),
                c,
            )
        val d2 = FieldP.add(d, d) // D = 2*((X1+B)²-A-C)
        val e = FieldP.add(FieldP.add(a, a), a) // E = 3*A
        val f = FieldP.sqr(e) // F = E²

        val x3 = FieldP.sub(f, FieldP.add(d2, d2)) // X3 = F - 2*D
        val c8 =
            FieldP.add(
                FieldP.add(FieldP.add(c, c), FieldP.add(c, c)),
                FieldP.add(FieldP.add(c, c), FieldP.add(c, c)),
            ) // 8*C
        val y3 = FieldP.sub(FieldP.mul(e, FieldP.sub(d2, x3)), c8) // Y3 = E*(D-X3) - 8*C
        val z3 =
            FieldP.sub(
                FieldP.sub(FieldP.sqr(FieldP.add(y, z)), FieldP.sqr(y)),
                FieldP.sqr(z),
            ) // Z3 = (Y1+Z1)² - B - Z1²  ... but B = Y1² so this = 2*Y1*Z1

        return JPoint(x3, y3, z3)
    }

    /**
     * Point addition in Jacobian coordinates.
     * Mixed addition when q.z = 1 (affine point) for efficiency.
     */
    fun add(
        p: JPoint,
        q: JPoint,
    ): JPoint {
        if (p.isInfinity()) return q
        if (q.isInfinity()) return p

        val z1sq = FieldP.sqr(p.z)
        val z2sq = FieldP.sqr(q.z)

        val u1 = FieldP.mul(p.x, z2sq) // U1 = X1*Z2²
        val u2 = FieldP.mul(q.x, z1sq) // U2 = X2*Z1²
        val s1 = FieldP.mul(p.y, FieldP.mul(q.z, z2sq)) // S1 = Y1*Z2³
        val s2 = FieldP.mul(q.y, FieldP.mul(p.z, z1sq)) // S2 = Y2*Z1³

        if (U256.cmp(u1, u2) == 0) {
            return if (U256.cmp(s1, s2) == 0) {
                double(p) // Same point
            } else {
                JPoint.INFINITY // Inverse points
            }
        }

        val h = FieldP.sub(u2, u1) // H = U2 - U1
        val i = FieldP.sqr(FieldP.add(h, h)) // I = (2*H)²
        val j = FieldP.mul(h, i) // J = H*I
        val r =
            FieldP.add(
                FieldP.sub(s2, s1),
                FieldP.sub(s2, s1),
            ) // r = 2*(S2-S1)
        val v = FieldP.mul(u1, i) // V = U1*I

        val x3 =
            FieldP.sub(
                FieldP.sub(FieldP.sqr(r), j),
                FieldP.add(v, v),
            ) // X3 = r² - J - 2*V
        val y3 =
            FieldP.sub(
                FieldP.mul(r, FieldP.sub(v, x3)),
                FieldP.add(FieldP.mul(s1, j), FieldP.mul(s1, j)),
            ) // Y3 = r*(V-X3) - 2*S1*J
        val z3 =
            FieldP.mul(
                FieldP.sub(
                    FieldP.sub(
                        FieldP.sqr(FieldP.add(p.z, q.z)),
                        z1sq,
                    ),
                    z2sq,
                ),
                h,
            ) // Z3 = ((Z1+Z2)²-Z1²-Z2²)*H

        return JPoint(x3, y3, z3)
    }

    /**
     * Scalar multiplication using double-and-add (left-to-right).
     */
    fun mul(
        p: JPoint,
        scalar: IntArray,
    ): JPoint {
        if (U256.isZero(scalar) || p.isInfinity()) return JPoint.INFINITY

        var result = JPoint.INFINITY
        // Find highest set bit
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(scalar, highBit)) highBit--

        for (i in highBit downTo 0) {
            result = double(result)
            if (U256.testBit(scalar, i)) {
                result = add(result, p)
            }
        }
        return result
    }

    /**
     * Convert Jacobian point to affine coordinates.
     * Returns null if the point is at infinity.
     */
    fun toAffine(p: JPoint): Pair<IntArray, IntArray>? {
        if (p.isInfinity()) return null
        val zInv = FieldP.inv(p.z)
        val zInv2 = FieldP.sqr(zInv)
        val zInv3 = FieldP.mul(zInv2, zInv)
        val x = FieldP.mul(p.x, zInv2)
        val y = FieldP.mul(p.y, zInv3)
        return Pair(x, y)
    }

    /**
     * Lift x-coordinate to a point on the curve.
     * Returns the point with even y if it exists, null otherwise.
     * Used by BIP-340 for x-only public keys.
     */
    fun liftX(x: IntArray): Pair<IntArray, IntArray>? {
        // Check x < p
        if (U256.cmp(x, FieldP.P) >= 0) return null

        // y² = x³ + 7
        val x3 = FieldP.mul(FieldP.sqr(x), x)
        val y2 = FieldP.add(x3, B)
        val y = FieldP.sqrt(y2) ?: return null

        // Return the even-y variant
        val yBytes = U256.toBytes(y)
        return if (yBytes[31].toInt() and 1 == 0) {
            Pair(x, y)
        } else {
            Pair(x, FieldP.neg(y))
        }
    }

    /** Check if y coordinate is even */
    fun hasEvenY(y: IntArray): Boolean {
        // y is even if the least significant bit is 0
        return y[0] and 1 == 0
    }

    /**
     * Parse a serialized public key (33 bytes compressed or 65 bytes uncompressed).
     * Returns affine (x, y) or null on failure.
     */
    fun parsePublicKey(pubkey: ByteArray): Pair<IntArray, IntArray>? {
        return when {
            pubkey.size == 33 && (pubkey[0] == 0x02.toByte() || pubkey[0] == 0x03.toByte()) -> {
                val x = U256.fromBytes(pubkey.copyOfRange(1, 33))
                if (U256.cmp(x, FieldP.P) >= 0) return null
                val x3 = FieldP.mul(FieldP.sqr(x), x)
                val y2 = FieldP.add(x3, B)
                val y = FieldP.sqrt(y2) ?: return null
                val isOdd = y[0] and 1 == 1
                val wantOdd = pubkey[0] == 0x03.toByte()
                if (isOdd != wantOdd) Pair(x, FieldP.neg(y)) else Pair(x, y)
            }

            pubkey.size == 65 && pubkey[0] == 0x04.toByte() -> {
                val x = U256.fromBytes(pubkey.copyOfRange(1, 33))
                val y = U256.fromBytes(pubkey.copyOfRange(33, 65))
                // Verify point is on curve: y² = x³ + 7
                val y2 = FieldP.sqr(y)
                val x3p7 = FieldP.add(FieldP.mul(FieldP.sqr(x), x), B)
                if (U256.cmp(y2, x3p7) != 0) return null
                Pair(x, y)
            }

            else -> {
                null
            }
        }
    }

    /** Serialize affine point as 65-byte uncompressed key (04 || x || y) */
    fun serializeUncompressed(
        x: IntArray,
        y: IntArray,
    ): ByteArray {
        val result = ByteArray(65)
        result[0] = 0x04
        U256.toBytes(x).copyInto(result, 1)
        U256.toBytes(y).copyInto(result, 33)
        return result
    }

    /** Serialize affine point as 33-byte compressed key (02/03 || x) */
    fun serializeCompressed(
        x: IntArray,
        y: IntArray,
    ): ByteArray {
        val result = ByteArray(33)
        result[0] = if (hasEvenY(y)) 0x02 else 0x03
        U256.toBytes(x).copyInto(result, 1)
        return result
    }
}
