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
 * Mutable Jacobian point for in-place computation.
 * (X, Y, Z) represents affine (X/Z², Y/Z³). Infinity: Z = 0.
 */
internal class MutablePoint(
    val x: IntArray = IntArray(8),
    val y: IntArray = IntArray(8),
    val z: IntArray = IntArray(8),
) {
    fun isInfinity(): Boolean = U256.isZero(z)

    fun setInfinity() {
        for (i in 0 until 8) {
            x[i] = 0
            z[i] = 0
        }
        y[0] = 1
        for (i in 1 until 8) y[i] = 0
    }

    fun copyFrom(other: MutablePoint) {
        other.x.copyInto(x)
        other.y.copyInto(y)
        other.z.copyInto(z)
    }

    fun setAffine(
        ax: IntArray,
        ay: IntArray,
    ) {
        ax.copyInto(x)
        ay.copyInto(y)
        z[0] = 1
        for (i in 1 until 8) z[i] = 0
    }

    fun snapshot(): MutablePoint = MutablePoint(x.copyOf(), y.copyOf(), z.copyOf())
}

/** Affine point stored as two IntArray(8). Used for precomputed tables. */
internal class AffinePoint(
    val x: IntArray = IntArray(8),
    val y: IntArray = IntArray(8),
)

internal object ECPoint {
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
    private val B = intArrayOf(7, 0, 0, 0, 0, 0, 0, 0)

    // GLV endomorphism: beta (cube root of unity in field, beta^3 = 1 mod p)
    // lambda*P = (beta*P.x, P.y) for any point P on the curve
    private val BETA =
        intArrayOf(
            0x719501EE.toInt(),
            0xC1396C28.toInt(),
            0x12F58995.toInt(),
            0x9CF04975.toInt(),
            0xAC3434E9.toInt(),
            0x6E64479E.toInt(),
            0x657C0710.toInt(),
            0x7AE96A2B.toInt(),
        )

    // GLV scalar decomposition constants (from libsecp256k1)
    // lambda: the scalar such that lambda*P = endomorphism(P)
    private val LAMBDA =
        intArrayOf(
            0x1B23BD72.toInt(),
            0xDF02967C.toInt(),
            0x20816678.toInt(),
            0x122E22EA.toInt(),
            0x8812645A.toInt(),
            0xA5261C02.toInt(),
            0xC05C30E0.toInt(),
            0x5363AD4C.toInt(),
        )

    // -lambda mod n
    private val MINUS_LAMBDA =
        intArrayOf(
            0xB512F0CF.toInt(),
            0xE0CF97D5.toInt(),
            0x8F279763.toInt(),
            0xA89CBA5C.toInt(),
            0x77EDE0E7.toInt(),
            0x09E86C02.toInt(),
            0xEF481860.toInt(),
            0x574B0E83.toInt(),
        )

    // g1 = round(2^384 * |b2| / n) for Babai rounding
    private val G1 =
        intArrayOf(
            0xEB153DAB.toInt(),
            0x90E49284.toInt(),
            0x6BCDE86C.toInt(),
            0xD221A7D4.toInt(),
            0x00003086,
            0,
            0,
            0,
        )

    // g2 = round(2^384 * |b1| / n)
    private val G2 =
        intArrayOf(
            0xE4C42212.toInt(),
            0x7FA90ABF.toInt(),
            0x88286F54.toInt(),
            0x7ED6010E.toInt(),
            0x0000E443.toInt(),
            0,
            0,
            0,
        )

    // -b1 mod n (b1 is negative, so this is |b1|)
    private val MINUS_B1 =
        intArrayOf(
            0x0ABFE4C3.toInt(),
            0x6F547FA9.toInt(),
            0x010E8828.toInt(),
            0xE4437ED6.toInt(),
            0,
            0,
            0,
            0,
        )

    // -b2 mod n
    private val MINUS_B2 =
        intArrayOf(
            0x3DB1562C.toInt(),
            0xD765CDA8.toInt(),
            0x0774346D.toInt(),
            0x8A280AC5.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    // Precomputed G table: gTable[i] = (i+1)*G as affine points
    private val gTable: Array<AffinePoint> by lazy { buildGTable() }

    private fun buildGTable(): Array<AffinePoint> {
        val jac = Array(16) { MutablePoint() }
        jac[0].setAffine(GX, GY)
        for (i in 1 until 16) {
            addPoints(jac[i], jac[i - 1], jac[0])
        }
        // Convert all to affine via batch-style (one inversion per point)
        return Array(16) { i ->
            val x = IntArray(8)
            val y = IntArray(8)
            toAffine(jac[i], x, y)
            AffinePoint(x, y)
        }
    }

    // Thread-local scratch
    private class PointScratch {
        val t = Array(12) { IntArray(8) }
        val dblCopy = MutablePoint()
    }

    private val scratch = ThreadLocal.withInitial { PointScratch() }

    // ============ Optimized Point Doubling (3M + 4S via fe_half) ============

    /**
     * Point doubling using the 3M+4S formula with fe_half.
     * L = (3/2)*X², S = Y², T = -X*S
     * X3 = L² + 2T, Y3 = -(L*(X3+T) + S²), Z3 = Y*Z
     */
    fun doublePoint(
        out: MutablePoint,
        inp: MutablePoint,
    ) {
        if (inp.isInfinity()) {
            out.setInfinity()
            return
        }
        val s = scratch.get()
        val p =
            if (out === inp) {
                s.dblCopy.copyFrom(inp)
                s.dblCopy
            } else {
                inp
            }
        val t = s.t

        // S = Y²
        FieldP.sqr(t[0], p.y)
        // L = (3/2)*X² = half(3*X²)
        FieldP.sqr(t[1], p.x) // X²
        FieldP.add(t[2], t[1], t[1]) // 2*X²
        FieldP.add(t[2], t[2], t[1]) // 3*X²
        FieldP.half(t[2], t[2]) // L = (3/2)*X²
        // T = -X*S
        FieldP.mul(t[3], p.x, t[0]) // X*S
        FieldP.neg(t[3], t[3]) // T = -X*S
        // X3 = L² + 2T
        FieldP.sqr(out.x, t[2]) // L²
        FieldP.add(out.x, out.x, t[3]) // + T
        FieldP.add(out.x, out.x, t[3]) // + T
        // Y3 = -(L*(X3+T) + S²)
        FieldP.add(t[4], out.x, t[3]) // X3+T
        FieldP.mul(t[4], t[2], t[4]) // L*(X3+T)
        FieldP.sqr(t[5], t[0]) // S²
        FieldP.add(t[4], t[4], t[5]) // L*(X3+T) + S²
        FieldP.neg(out.y, t[4]) // negate
        // Z3 = Y*Z
        FieldP.mul(out.z, p.y, p.z)
    }

    // ============ Mixed Addition: Jacobian + Affine (8M + 3S) ============

    /**
     * Mixed addition: out = p + (qx, qy) where q is affine (z=1).
     * Saves 4M+1S vs full Jacobian addition.
     */
    fun addMixed(
        out: MutablePoint,
        p: MutablePoint,
        qx: IntArray,
        qy: IntArray,
    ) {
        if (p.isInfinity()) {
            out.setAffine(qx, qy)
            return
        }
        val s = scratch.get()
        val t = s.t

        // Z1² and Z1³
        FieldP.sqr(t[0], p.z) // Z1²
        FieldP.mul(t[1], t[0], p.z) // Z1³
        // U2 = qx * Z1², S2 = qy * Z1³ (U1=X1, S1=Y1 since q.z=1)
        FieldP.mul(t[2], qx, t[0]) // U2
        FieldP.mul(t[3], qy, t[1]) // S2
        // H = U2 - X1
        FieldP.sub(t[4], t[2], p.x) // H

        if (U256.isZero(t[4])) {
            // U1 == U2, check S1 vs S2
            val tmp = IntArray(8)
            FieldP.sub(tmp, t[3], p.y)
            if (U256.isZero(tmp)) {
                doublePoint(out, p)
            } else {
                out.setInfinity()
            }
            return
        }

        // I = (2H)², J = H*I
        FieldP.add(t[5], t[4], t[4]) // 2H
        FieldP.sqr(t[5], t[5]) // I = (2H)²
        FieldP.mul(t[6], t[4], t[5]) // J = H*I
        // r = 2*(S2 - Y1)
        FieldP.sub(t[7], t[3], p.y)
        FieldP.add(t[7], t[7], t[7]) // r
        // V = X1 * I
        FieldP.mul(t[8], p.x, t[5]) // V
        // X3 = r² - J - 2V
        FieldP.sqr(out.x, t[7])
        FieldP.sub(out.x, out.x, t[6])
        FieldP.sub(out.x, out.x, t[8])
        FieldP.sub(out.x, out.x, t[8])
        // Y3 = r*(V - X3) - 2*Y1*J
        FieldP.sub(t[9], t[8], out.x)
        FieldP.mul(out.y, t[7], t[9])
        FieldP.mul(t[9], p.y, t[6]) // Y1*J
        FieldP.add(t[9], t[9], t[9]) // 2*Y1*J
        FieldP.sub(out.y, out.y, t[9])
        // Z3 = 2*Z1*H
        FieldP.mul(out.z, p.z, t[4])
        FieldP.add(out.z, out.z, out.z) // *2
    }

    // ============ Full Jacobian Addition (kept for table building) ============

    fun addPoints(
        out: MutablePoint,
        p: MutablePoint,
        q: MutablePoint,
    ) {
        if (p.isInfinity()) {
            out.copyFrom(q)
            return
        }
        if (q.isInfinity()) {
            out.copyFrom(p)
            return
        }
        val s = scratch.get()
        val t = s.t

        FieldP.sqr(t[0], p.z)
        FieldP.sqr(t[1], q.z)
        FieldP.mul(t[2], p.x, t[1])
        FieldP.mul(t[3], q.x, t[0])
        FieldP.mul(t[4], q.z, t[1])
        FieldP.mul(t[4], p.y, t[4])
        FieldP.mul(t[5], p.z, t[0])
        FieldP.mul(t[5], q.y, t[5])

        if (U256.cmp(t[2], t[3]) == 0) {
            if (U256.cmp(t[4], t[5]) == 0) {
                doublePoint(out, p)
            } else {
                out.setInfinity()
            }
            return
        }

        FieldP.sub(t[6], t[3], t[2])
        FieldP.add(t[7], t[6], t[6])
        FieldP.sqr(t[7], t[7])
        FieldP.mul(t[8], t[6], t[7])
        FieldP.sub(t[9], t[5], t[4])
        FieldP.add(t[9], t[9], t[9])
        FieldP.mul(t[10], t[2], t[7])

        FieldP.sqr(out.x, t[9])
        FieldP.sub(out.x, out.x, t[8])
        FieldP.sub(out.x, out.x, t[10])
        FieldP.sub(out.x, out.x, t[10])
        FieldP.sub(t[11], t[10], out.x)
        FieldP.mul(out.y, t[9], t[11])
        FieldP.mul(t[11], t[4], t[8])
        FieldP.add(t[11], t[11], t[11])
        FieldP.sub(out.y, out.y, t[11])
        FieldP.add(out.z, p.z, q.z)
        FieldP.sqr(out.z, out.z)
        FieldP.sub(out.z, out.z, t[0])
        FieldP.sub(out.z, out.z, t[1])
        FieldP.mul(out.z, out.z, t[6])
    }
    // ============ wNAF Encoding ============

    /** Convert scalar to width-w wNAF. Returns array of signed digits and the number of used bits. */
    fun wnaf(
        scalar: IntArray,
        w: Int,
        maxBits: Int,
    ): IntArray {
        val result = IntArray(maxBits + 1)
        // Work on a mutable copy
        val s = scalar.copyOf()
        var bit = 0
        while (bit < maxBits) {
            if (s[bit / 32] ushr (bit % 32) and 1 == 0) {
                bit++
                continue
            }
            // Extract w bits
            var word = getBitsVar(s, bit, w.coerceAtMost(maxBits - bit))
            if (word >= (1 shl (w - 1))) {
                word -= (1 shl w)
                // Propagate the borrow
                addBitTo(s, bit + w)
            }
            result[bit] = word
            bit += w
        }
        return result
    }

    private fun getBitsVar(
        s: IntArray,
        bitPos: Int,
        count: Int,
    ): Int {
        if (count == 0) return 0
        val limb = bitPos / 32
        val shift = bitPos % 32
        var r = (s[limb] ushr shift)
        if (shift + count > 32 && limb + 1 < s.size) {
            r = r or (s[limb + 1] shl (32 - shift))
        }
        return r and ((1 shl count) - 1)
    }

    private fun addBitTo(
        s: IntArray,
        bitPos: Int,
    ) {
        val limb = bitPos / 32
        if (limb >= s.size) return
        val bit = bitPos % 32
        var carry = (1L shl bit)
        for (i in limb until s.size) {
            carry += (s[i].toLong() and 0xFFFFFFFFL)
            s[i] = carry.toInt()
            carry = carry ushr 32
            if (carry == 0L) break
        }
    }

    // ============ GLV Endomorphism ============

    /** Apply endomorphism: (x, y) -> (beta*x, y) */
    fun mulLambdaAffine(src: AffinePoint): AffinePoint {
        val nx = IntArray(8)
        FieldP.mul(nx, src.x, BETA)
        return AffinePoint(nx, src.y.copyOf())
    }

    /**
     * Split scalar k into k1, k2 such that k = k1 + k2*lambda (mod n),
     * where |k1|, |k2| are ~128 bits. Returns (k1, k2, negK1, negK2)
     * where negK1/negK2 indicate if the point should be negated.
     */
    fun scalarSplitLambda(k: IntArray): SplitResult {
        // c1 = round(k * g1 >> 384), c2 = round(k * g2 >> 384)
        val c1 = U256.mulShift(k, G1, 384)
        val c2 = U256.mulShift(k, G2, 384)

        // r2 = c1*(-b1) + c2*(-b2) mod n
        val c1b1 = ScalarN.mul(c1, MINUS_B1)
        val c2b2 = ScalarN.mul(c2, MINUS_B2)
        val r2 = ScalarN.add(c1b1, c2b2)

        // r1 = k + r2*(-lambda) mod n = k - r2*lambda mod n
        val r2lam = ScalarN.mul(r2, MINUS_LAMBDA)
        val r1 = ScalarN.add(r2lam, k)

        // If r1 or r2 > n/2, negate them (and we'll negate the corresponding point)
        val negK1 = isHigh(r1)
        val negK2 = isHigh(r2)
        val k1 = if (negK1) ScalarN.neg(r1) else r1
        val k2 = if (negK2) ScalarN.neg(r2) else r2

        return SplitResult(k1, k2, negK1, negK2)
    }

    class SplitResult(
        val k1: IntArray,
        val k2: IntArray,
        val negK1: Boolean,
        val negK2: Boolean,
    )

    /** Check if scalar > n/2 */
    private fun isHigh(s: IntArray): Boolean {
        // n/2 = 7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0
        // Simple check: if top bit of byte 31 (bit 255) is set, it's > n/2
        // More precise: compare against n/2
        val nHalf =
            intArrayOf(
                0x681B20A0.toInt(),
                0xDFE92F46.toInt(),
                0x57A4501D.toInt(),
                0x5D576E73.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFFFFFF.toInt(),
                0x7FFFFFFF.toInt(),
            )
        return U256.cmp(s, nHalf) > 0
    }

    // ============ Strauss with GLV: s*G + e*P in one pass ============

    /**
     * Compute s*G + e*P using GLV endomorphism and interleaved wNAF (Strauss method).
     * Splits each scalar into two 128-bit halves, processes 4 wNAF streams simultaneously.
     * Uses mixed Jacobian+Affine addition with precomputed affine tables.
     */
    fun straussGlvGP(
        out: MutablePoint,
        s: IntArray,
        px: IntArray,
        py: IntArray,
        e: IntArray,
    ) {
        val windowA = 5 // for arbitrary point P
        val tableSize = 1 shl (windowA - 2) // 8 entries

        // Split scalars via GLV
        val sSplit = scalarSplitLambda(s)
        val eSplit = scalarSplitLambda(e)

        // Build wNAF for each half-scalar (128 bits)
        val wnafS1 = wnaf(sSplit.k1, windowA, 129)
        val wnafS2 = wnaf(sSplit.k2, windowA, 129)
        val wnafE1 = wnaf(eSplit.k1, windowA, 129)
        val wnafE2 = wnaf(eSplit.k2, windowA, 129)

        // Build base table for P (no negation), then derive negated versions
        val pTableBase = buildOddMultiplesTable(px, py, tableSize, false)
        val pTable =
            if (eSplit.negK1) {
                Array(tableSize) { i ->
                    val ny = IntArray(8)
                    FieldP.neg(ny, pTableBase[i].y)
                    AffinePoint(pTableBase[i].x, ny)
                }
            } else {
                pTableBase
            }
        // Build lambda(P) table from base (not pre-negated) table, then apply negK2
        val pLamBase = Array(tableSize) { mulLambdaAffine(pTableBase[it]) }
        val pLamTable =
            if (eSplit.negK2) {
                Array(tableSize) { i ->
                    val ny = IntArray(8)
                    FieldP.neg(ny, pLamBase[i].y)
                    AffinePoint(pLamBase[i].x, ny)
                }
            } else {
                pLamBase
            }

        // G table: odd multiples [1G, 3G, 5G, ..., 15G] (base, un-negated)
        val gOdd = Array(tableSize) { gTable[it * 2] }
        val gOddSigned =
            if (sSplit.negK1) {
                Array(tableSize) { i ->
                    val ny = IntArray(8)
                    FieldP.neg(ny, gOdd[i].y)
                    AffinePoint(gOdd[i].x, ny)
                }
            } else {
                gOdd
            }
        // Lambda(G) table: from un-negated base
        val gLamOdd = Array(tableSize) { mulLambdaAffine(gOdd[it]) }
        val gLamOddSigned =
            if (sSplit.negK2) {
                Array(tableSize) { i ->
                    val ny = IntArray(8)
                    FieldP.neg(ny, gLamOdd[i].y)
                    AffinePoint(gLamOdd[i].x, ny)
                }
            } else {
                gLamOdd
            }

        // Handle negation from GLV split for s
        // If negK1 for s: negate the G table entries y-coords (flip sign)
        // If negK2 for s: negate the Glam table entries y-coords
        // Simpler: we encode the negation into the wNAF lookup

        // Find highest bit across all 4 wNAFs
        var bits = 129
        while (bits > 0 && wnafS1[bits - 1] == 0 && wnafS2[bits - 1] == 0 &&
            wnafE1[bits - 1] == 0 && wnafE2[bits - 1] == 0
        ) {
            bits--
        }

        out.setInfinity()
        val tmp = MutablePoint()
        for (i in bits - 1 downTo 0) {
            doublePoint(out, out)

            // Stream 1: s1 * G (sign baked into gOddSigned)
            val n1 = wnafS1[i]
            if (n1 != 0) {
                val idx = (if (n1 > 0) n1 else -n1) / 2
                addMixedWithSign(tmp, out, gOddSigned[idx], n1 < 0)
                out.copyFrom(tmp)
            }
            // Stream 2: s2 * lambda(G) (sign baked into gLamOddSigned)
            val n2 = wnafS2[i]
            if (n2 != 0) {
                val idx = (if (n2 > 0) n2 else -n2) / 2
                addMixedWithSign(tmp, out, gLamOddSigned[idx], n2 < 0)
                out.copyFrom(tmp)
            }
            // Stream 3: e1 * P (sign baked into pTable)
            val n3 = wnafE1[i]
            if (n3 != 0) {
                val idx = (if (n3 > 0) n3 else -n3) / 2
                addMixedWithSign(tmp, out, pTable[idx], n3 < 0)
                out.copyFrom(tmp)
            }
            // Stream 4: e2 * lambda(P) (sign baked into pLamTable)
            val n4 = wnafE2[i]
            if (n4 != 0) {
                val idx = (if (n4 > 0) n4 else -n4) / 2
                addMixedWithSign(tmp, out, pLamTable[idx], n4 < 0)
                out.copyFrom(tmp)
            }
        }
    }

    /** Add affine point with optional y-negation */
    private fun addMixedWithSign(
        out: MutablePoint,
        p: MutablePoint,
        q: AffinePoint,
        negateQ: Boolean,
    ) {
        if (negateQ) {
            val negY = IntArray(8)
            FieldP.neg(negY, q.y)
            addMixed(out, p, q.x, negY)
        } else {
            addMixed(out, p, q.x, q.y)
        }
    }

    /** Build table of odd multiples [1,3,5,...,(2*n-1)]*P as affine points */
    private fun buildOddMultiplesTable(
        px: IntArray,
        py: IntArray,
        n: Int,
        negate: Boolean,
    ): Array<AffinePoint> {
        val p = MutablePoint()
        p.setAffine(px, py)

        // 2*P for stepping
        val p2 = MutablePoint()
        doublePoint(p2, p)

        val jPoints = Array(n) { MutablePoint() }
        jPoints[0].copyFrom(p) // 1*P
        for (i in 1 until n) {
            addPoints(jPoints[i], jPoints[i - 1], p2) // (2i+1)*P
        }

        // Convert to affine
        return Array(n) { i ->
            val x = IntArray(8)
            val y = IntArray(8)
            toAffine(jPoints[i], x, y)
            if (negate) FieldP.neg(y, y)
            AffinePoint(x, y)
        }
    }
    // ============ Generic scalar multiplication (for non-verify paths) ============

    fun mul(
        out: MutablePoint,
        p: MutablePoint,
        scalar: IntArray,
    ) {
        if (U256.isZero(scalar) || p.isInfinity()) {
            out.setInfinity()
            return
        }
        // Build 4-bit window table (16 entries, Jacobian)
        val table = Array(16) { MutablePoint() }
        table[0].copyFrom(p)
        for (i in 1 until 16) addPoints(table[i], table[i - 1], p)

        out.setInfinity()
        val tmp = MutablePoint()
        for (nibbleIdx in 63 downTo 0) {
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            val nib = U256.getNibble(scalar, nibbleIdx)
            if (nib != 0) {
                addPoints(tmp, out, table[nib - 1])
                out.copyFrom(tmp)
            }
        }
    }

    fun mulG(
        out: MutablePoint,
        scalar: IntArray,
    ) {
        if (U256.isZero(scalar)) {
            out.setInfinity()
            return
        }
        val table = gTable
        out.setInfinity()
        val tmp = MutablePoint()
        for (nibbleIdx in 63 downTo 0) {
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            val nib = U256.getNibble(scalar, nibbleIdx)
            if (nib != 0) {
                addMixed(tmp, out, table[nib - 1].x, table[nib - 1].y)
                out.copyFrom(tmp)
            }
        }
    }

    fun mulDoubleG(
        out: MutablePoint,
        s: IntArray,
        p: MutablePoint,
        e: IntArray,
    ) {
        // Shamir's trick: s*G + e*P in one pass with 4-bit windows
        // G table: precomputed affine (mixed addition = 8M+3S)
        // P table: Jacobian on-the-fly (full addition = 11M+5S, but no inversion cost)
        val gTab = gTable
        val pTab = Array(16) { MutablePoint() }
        pTab[0].copyFrom(p)
        for (i in 1 until 16) addPoints(pTab[i], pTab[i - 1], pTab[0])

        out.setInfinity()
        val tmp = MutablePoint()
        for (nibbleIdx in 63 downTo 0) {
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            val sNib = U256.getNibble(s, nibbleIdx)
            if (sNib != 0) {
                addMixed(tmp, out, gTab[sNib - 1].x, gTab[sNib - 1].y)
                out.copyFrom(tmp)
            }
            val eNib = U256.getNibble(e, nibbleIdx)
            if (eNib != 0) {
                addPoints(tmp, out, pTab[eNib - 1])
                out.copyFrom(tmp)
            }
        }
    }

    // ============ Coordinate conversion and serialization ============

    fun toAffine(
        p: MutablePoint,
        outX: IntArray,
        outY: IntArray,
    ): Boolean {
        if (p.isInfinity()) return false
        val zInv = IntArray(8)
        val zInv2 = IntArray(8)
        val zInv3 = IntArray(8)
        FieldP.inv(zInv, p.z)
        FieldP.sqr(zInv2, zInv)
        FieldP.mul(zInv3, zInv2, zInv)
        FieldP.mul(outX, p.x, zInv2)
        FieldP.mul(outY, p.y, zInv3)
        return true
    }

    fun liftX(
        outX: IntArray,
        outY: IntArray,
        x: IntArray,
    ): Boolean {
        if (U256.cmp(x, FieldP.P) >= 0) return false
        val t = IntArray(8)
        FieldP.sqr(t, x)
        FieldP.mul(t, t, x)
        FieldP.add(t, t, B)
        if (!FieldP.sqrt(outY, t)) return false
        U256.copyInto(outX, x)
        if (outY[0] and 1 != 0) FieldP.neg(outY, outY)
        return true
    }

    fun hasEvenY(y: IntArray): Boolean = y[0] and 1 == 0

    fun parsePublicKey(
        pubkey: ByteArray,
        outX: IntArray,
        outY: IntArray,
    ): Boolean {
        return when {
            pubkey.size == 33 && (pubkey[0] == 0x02.toByte() || pubkey[0] == 0x03.toByte()) -> {
                val x = U256.fromBytes(pubkey.copyOfRange(1, 33))
                if (U256.cmp(x, FieldP.P) >= 0) return false
                val t = IntArray(8)
                FieldP.sqr(t, x)
                FieldP.mul(t, t, x)
                FieldP.add(t, t, B)
                if (!FieldP.sqrt(outY, t)) return false
                U256.copyInto(outX, x)
                val isOdd = outY[0] and 1 == 1
                val wantOdd = pubkey[0] == 0x03.toByte()
                if (isOdd != wantOdd) FieldP.neg(outY, outY)
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
    }

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

    fun serializeCompressed(
        x: IntArray,
        y: IntArray,
    ): ByteArray {
        val r = ByteArray(33)
        r[0] = if (hasEvenY(y)) 0x02 else 0x03
        U256.toBytesInto(x, r, 1)
        return r
    }

    fun toAffinePair(p: MutablePoint): Pair<IntArray, IntArray>? {
        val x = IntArray(8)
        val y = IntArray(8)
        return if (toAffine(p, x, y)) Pair(x, y) else null
    }
}
