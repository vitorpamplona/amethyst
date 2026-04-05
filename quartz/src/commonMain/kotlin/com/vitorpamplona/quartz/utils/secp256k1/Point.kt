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

    /** Create a snapshot (immutable copy for table storage) */
    fun snapshot(): MutablePoint = MutablePoint(x.copyOf(), y.copyOf(), z.copyOf())
}

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
    private val ONE = intArrayOf(1, 0, 0, 0, 0, 0, 0, 0)

    // Precomputed table for G: gTable[i] = (i+1)*G for i in 0..15
    // Lazily initialized on first use.
    private val gTable: Array<MutablePoint> by lazy { buildGTable() }

    private fun buildGTable(): Array<MutablePoint> {
        val table = Array(16) { MutablePoint() }
        // table[0] = G
        table[0].setAffine(GX, GY)
        // table[i] = table[i-1] + G
        val tmp = MutablePoint()
        for (i in 1 until 16) {
            addPoints(table[i], table[i - 1], table[0])
        }
        return Array(16) { table[it].snapshot() }
    }

    // ============ Scratch buffers for point operations (thread-local) ============
    private class PointScratch {
        val t = Array(12) { IntArray(8) } // temporary field elements
        val dblCopy = MutablePoint() // copy buffer for in-place doubling
    }

    private val scratch = ThreadLocal.withInitial { PointScratch() }

    /**
     * Point doubling: out = 2*p.
     * Formula: dbl-2009-l from https://hyperelliptic.org/EFD/g1p/auto-shortw-jacobian-0.html
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
        // If out aliases inp, copy inp to scratch first
        val p =
            if (out === inp) {
                s.dblCopy.copyFrom(inp)
                s.dblCopy
            } else {
                inp
            }
        val t = s.t
        // t0=A=X², t1=B=Y², t2=C=B², t3=(X+B)²
        FieldP.sqr(t[0], p.x)
        FieldP.sqr(t[1], p.y)
        FieldP.sqr(t[2], t[1])
        FieldP.add(t[3], p.x, t[1])
        FieldP.sqr(t[3], t[3])
        // t3 = D = 2*((X+B)²-A-C)
        FieldP.sub(t[3], t[3], t[0])
        FieldP.sub(t[3], t[3], t[2])
        FieldP.add(t[3], t[3], t[3]) // D
        // t4 = E = 3*A
        FieldP.add(t[4], t[0], t[0])
        FieldP.add(t[4], t[4], t[0])
        // t5 = F = E²
        FieldP.sqr(t[5], t[4])
        // X3 = F - 2*D
        FieldP.add(t[6], t[3], t[3]) // 2D
        FieldP.sub(out.x, t[5], t[6])
        // Y3 = E*(D-X3) - 8*C
        FieldP.sub(t[7], t[3], out.x)
        FieldP.mul(t[7], t[4], t[7])
        FieldP.add(t[2], t[2], t[2]) // 2C
        FieldP.add(t[2], t[2], t[2]) // 4C
        FieldP.add(t[2], t[2], t[2]) // 8C
        FieldP.sub(out.y, t[7], t[2])
        // Z3 = 2*Y*Z
        FieldP.add(t[8], p.y, p.z)
        FieldP.sqr(t[8], t[8])
        FieldP.sub(t[8], t[8], t[1]) // -B
        FieldP.sqr(t[9], p.z)
        FieldP.sub(out.z, t[8], t[9])
    }

    /**
     * Point addition: out = p + q. Handles p==q (doubling) and inverses.
     */
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

        FieldP.sqr(t[0], p.z) // Z1²
        FieldP.sqr(t[1], q.z) // Z2²
        FieldP.mul(t[2], p.x, t[1]) // U1 = X1*Z2²
        FieldP.mul(t[3], q.x, t[0]) // U2 = X2*Z1²
        FieldP.mul(t[4], q.z, t[1]) // Z2³
        FieldP.mul(t[4], p.y, t[4]) // S1 = Y1*Z2³
        FieldP.mul(t[5], p.z, t[0]) // Z1³
        FieldP.mul(t[5], q.y, t[5]) // S2 = Y2*Z1³

        if (U256.cmp(t[2], t[3]) == 0) {
            if (U256.cmp(t[4], t[5]) == 0) {
                doublePoint(out, p)
            } else {
                out.setInfinity()
            }
            return
        }

        FieldP.sub(t[6], t[3], t[2]) // H = U2-U1
        FieldP.add(t[7], t[6], t[6]) // 2H
        FieldP.sqr(t[7], t[7]) // I = (2H)²
        FieldP.mul(t[8], t[6], t[7]) // J = H*I
        FieldP.sub(t[9], t[5], t[4])
        FieldP.add(t[9], t[9], t[9]) // r = 2*(S2-S1)
        FieldP.mul(t[10], t[2], t[7]) // V = U1*I
        // X3 = r² - J - 2V
        FieldP.sqr(out.x, t[9])
        FieldP.sub(out.x, out.x, t[8])
        FieldP.sub(out.x, out.x, t[10])
        FieldP.sub(out.x, out.x, t[10])
        // Y3 = r*(V-X3) - 2*S1*J
        FieldP.sub(t[11], t[10], out.x)
        FieldP.mul(out.y, t[9], t[11])
        FieldP.mul(t[11], t[4], t[8]) // S1*J
        FieldP.add(t[11], t[11], t[11]) // 2*S1*J
        FieldP.sub(out.y, out.y, t[11])
        // Z3 = ((Z1+Z2)²-Z1²-Z2²)*H
        FieldP.add(out.z, p.z, q.z)
        FieldP.sqr(out.z, out.z)
        FieldP.sub(out.z, out.z, t[0])
        FieldP.sub(out.z, out.z, t[1])
        FieldP.mul(out.z, out.z, t[6])
    }

    /**
     * Scalar multiplication: out = scalar * p, using 4-bit windowed method.
     */
    fun mul(
        out: MutablePoint,
        p: MutablePoint,
        scalar: IntArray,
    ) {
        if (U256.isZero(scalar) || p.isInfinity()) {
            out.setInfinity()
            return
        }

        // Build 4-bit window table: table[i] = (i+1)*p for i in 0..15
        val table = Array(16) { MutablePoint() }
        table[0].copyFrom(p)
        val tmp = MutablePoint()
        for (i in 1 until 16) {
            addPoints(table[i], table[i - 1], p)
        }

        out.setInfinity()
        // Process 4 bits at a time, MSB first (64 nibbles for 256 bits)
        for (nibbleIdx in 63 downTo 0) {
            // 4 doublings
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

    /**
     * G multiplication using precomputed table: out = scalar * G.
     * Uses 4-bit windowed method with static precomputed table.
     */
    fun mulG(
        out: MutablePoint,
        scalar: IntArray,
    ) {
        if (U256.isZero(scalar)) {
            out.setInfinity()
            return
        }
        val table = gTable // force lazy init
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

    /**
     * Shamir's trick: out = s*G + e*P in a single pass.
     * Much faster than computing s*G and e*P separately for verification.
     */
    fun mulDoubleG(
        out: MutablePoint,
        s: IntArray,
        p: MutablePoint,
        e: IntArray,
    ) {
        // Build 4-bit window table for P
        val pTable = Array(16) { MutablePoint() }
        pTable[0].copyFrom(p)
        for (i in 1 until 16) {
            addPoints(pTable[i], pTable[i - 1], p)
        }
        val gTab = gTable
        val tmp = MutablePoint()

        out.setInfinity()
        for (nibbleIdx in 63 downTo 0) {
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            doublePoint(out, out)
            val sNib = U256.getNibble(s, nibbleIdx)
            val eNib = U256.getNibble(e, nibbleIdx)
            if (sNib != 0) {
                addPoints(tmp, out, gTab[sNib - 1])
                out.copyFrom(tmp)
            }
            if (eNib != 0) {
                addPoints(tmp, out, pTable[eNib - 1])
                out.copyFrom(tmp)
            }
        }
    }

    /** Convert Jacobian to affine. Writes x, y into outX, outY. Returns false if infinity. */
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

    /** Lift x-coordinate to even-y point. Returns false if not on curve. */
    fun liftX(
        outX: IntArray,
        outY: IntArray,
        x: IntArray,
    ): Boolean {
        if (U256.cmp(x, FieldP.P) >= 0) return false
        val t = IntArray(8)
        FieldP.sqr(t, x)
        FieldP.mul(t, t, x) // x³
        FieldP.add(t, t, B) // x³+7
        if (!FieldP.sqrt(outY, t)) return false
        U256.copyInto(outX, x)
        // Ensure even y
        if (outY[0] and 1 != 0) FieldP.neg(outY, outY)
        return true
    }

    fun hasEvenY(y: IntArray): Boolean = y[0] and 1 == 0

    /** Parse serialized public key -> affine (outX, outY). Returns false on failure. */
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

    // Convenience wrappers for non-hot paths
    fun toAffinePair(p: MutablePoint): Pair<IntArray, IntArray>? {
        val x = IntArray(8)
        val y = IntArray(8)
        return if (toAffine(p, x, y)) Pair(x, y) else null
    }
}
