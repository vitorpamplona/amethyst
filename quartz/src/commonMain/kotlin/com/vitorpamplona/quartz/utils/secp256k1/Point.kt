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
// ELLIPTIC CURVE POINT OPERATIONS ON secp256k1
// =====================================================================================
//
// This file implements point arithmetic on the secp256k1 elliptic curve: y² = x³ + 7 (mod p).
// It provides point addition, doubling, scalar multiplication, and serialization.
//
// JACOBIAN COORDINATES
// ====================
// Points are stored in Jacobian projective coordinates (X, Y, Z) which represent the
// affine point (X/Z², Y/Z³). This avoids expensive field inversions during intermediate
// steps of scalar multiplication — inversion is only needed once at the very end to
// convert back to affine (x, y) form.
//
// The "point at infinity" (identity element) is represented by Z = 0.
//
// PRECOMPUTED GENERATOR TABLE
// ===========================
// The generator point G is used for public key creation and signature operations.
// We precompute a table of [1G, 2G, 3G, ..., 16G] in affine form (stored as AffinePoint)
// at first use. This table is lazily initialized and cached for the lifetime of the process.
// Affine storage enables "mixed addition" (Jacobian + Affine), which is cheaper than
// adding two Jacobian points because the second point's Z=1 eliminates several multiplications.
//
// POINT DOUBLING FORMULA
// ======================
// We use the 3M+4S formula from libsecp256k1 that computes L = (3/2)·X² using a cheap
// field halving operation instead of a full multiplication. On the secp256k1 curve (a=0),
// this is the most efficient known doubling formula.
//
// SCALAR MULTIPLICATION
// =====================
// For general scalar multiplication (mul, mulG), we use a 4-bit windowed method.
// For signature verification (mulDoubleG), we use Shamir's trick combined with:
//
// - GLV Endomorphism (Glv.kt): Splits each 256-bit scalar into two ~128-bit halves,
//   halving the number of doublings from 256 to ~130.
// - wNAF-5 Encoding (Glv.kt): Encodes each half-scalar so non-zero digits are sparse
//   (separated by ≥4 zeros), reducing point additions.
// - Mixed Addition: G-side additions use the precomputed affine table (8M+3S per add),
//   while P-side uses full Jacobian (11M+5S, avoiding expensive table inversions).
// =====================================================================================

/**
 * Mutable Jacobian point for in-place computation.
 *
 * Points are mutable to avoid allocating new objects during the inner loop of scalar
 * multiplication, which performs thousands of doublings and additions per operation.
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
}

/**
 * Affine point (x, y) — no Z coordinate.
 * Used for precomputed tables where we want compact storage and mixed addition.
 */
internal class AffinePoint(
    val x: IntArray = IntArray(8),
    val y: IntArray = IntArray(8),
)

internal object ECPoint {
    // ==================== Generator point G ====================

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

    /** Curve constant b = 7 in y² = x³ + 7. */
    private val B = intArrayOf(7, 0, 0, 0, 0, 0, 0, 0)

    /**
     * wNAF window width for the G-side of scalar multiplication.
     * Width w uses a table of 2^(w-2) odd multiples. Larger windows mean fewer
     * additions but more precomputed storage:
     *   w=5:  8 entries,  ~26 adds per 128-bit scalar (~1KB table)
     *   w=8:  64 entries, ~16 adds per 128-bit scalar (~8KB table)
     *   w=10: 256 entries,~13 adds per 128-bit scalar (~32KB table)
     */
    private const val WINDOW_G = 8
    private val G_TABLE_SIZE = 1 shl (WINDOW_G - 2) // 64 for w=8

    /**
     * Precomputed G odd-multiples for wNAF: gOddTable[i] = (2i+1)·G as affine, for i in 0..G_TABLE_SIZE-1.
     * Used by mulG and mulDoubleG. Lazily initialized on first use.
     */
    private val gOddTable: Array<AffinePoint> by lazy { buildGOddTable() }

    /** Precomputed λ(G) odd-multiples for GLV: gLamTable[i] = λ((2i+1)·G) as affine. */
    private val gLamTable: Array<AffinePoint> by lazy {
        Array(G_TABLE_SIZE) { AffinePoint(FieldP.mul(gOddTable[it].x, Glv.BETA), gOddTable[it].y.copyOf()) }
    }

    private fun buildGOddTable(): Array<AffinePoint> {
        val g = MutablePoint()
        g.setAffine(GX, GY)
        val g2 = MutablePoint()
        doublePoint(g2, g)

        val jac = Array(G_TABLE_SIZE) { MutablePoint() }
        jac[0].copyFrom(g)
        for (i in 1 until G_TABLE_SIZE) addPoints(jac[i], jac[i - 1], g2)

        return Array(G_TABLE_SIZE) { i ->
            val x = IntArray(8)
            val y = IntArray(8)
            toAffine(jac[i], x, y)
            AffinePoint(x, y)
        }
    }

    // ==================== Thread-local scratch buffers ====================

    /**
     * Scratch space for point operations. Each thread gets its own set of temporary
     * field elements to avoid allocation in the inner loops. The 12 temp buffers
     * (t[0]..t[11]) are shared across doublePoint and addPoints — this is safe because
     * these functions only call each other in the equal-point degenerate case, which
     * returns immediately after the recursive call without using the temps further.
     */
    private class PointScratch {
        val t = Array(12) { IntArray(8) }
        val dblCopy = MutablePoint() // Copy buffer for in-place doubling (out === input)
    }

    private val scratch = ThreadLocal.withInitial { PointScratch() }

    // ==================== Point Doubling (3M + 4S) ====================

    /**
     * Point doubling: out = 2·p.
     *
     * Uses the optimized formula from libsecp256k1 for curves with a=0:
     *   S = Y², L = (3/2)·X², T = -X·S
     *   X₃ = L² + 2T,  Y₃ = -(L·(X₃+T) + S²),  Z₃ = Y·Z
     *
     * Cost: 3 multiplications + 4 squarings + field halving + adds/negations.
     * The key trick is computing L = (3/2)·X² using fe_half instead of an extra
     * multiplication — halving is just a carry-propagating right shift.
     *
     * Safe for out === inp (in-place doubling) via internal copy buffer.
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

        FieldP.sqr(t[0], p.y) // S = Y²
        FieldP.sqr(t[1], p.x) // X²
        FieldP.add(t[2], t[1], t[1]) // 2·X²
        FieldP.add(t[2], t[2], t[1]) // 3·X²
        FieldP.half(t[2], t[2]) // L = (3/2)·X²
        FieldP.mul(t[3], p.x, t[0]) // X·S
        FieldP.neg(t[3], t[3]) // T = -X·S
        FieldP.sqr(out.x, t[2]) // X₃ = L²
        FieldP.add(out.x, out.x, t[3]) //     + T
        FieldP.add(out.x, out.x, t[3]) //     + T
        FieldP.add(t[4], out.x, t[3]) // X₃ + T
        FieldP.mul(t[4], t[2], t[4]) // L·(X₃+T)
        FieldP.sqr(t[5], t[0]) // S²
        FieldP.add(t[4], t[4], t[5]) // L·(X₃+T) + S²
        FieldP.neg(out.y, t[4]) // Y₃ = negate
        FieldP.mul(out.z, p.y, p.z) // Z₃ = Y·Z
    }

    // ==================== Mixed Addition: Jacobian + Affine (8M + 3S) ====================

    /**
     * Mixed point addition: out = p + (qx, qy) where q is an affine point (Z=1).
     *
     * When one input is affine, we can skip computing Z₂², Z₂³, and the Z₃ formula
     * simplifies. This saves 4 multiplications and 1 squaring vs full Jacobian addition.
     *
     * Cost: 8 multiplications + 3 squarings + adds/subtractions.
     * Used for additions from the precomputed G table (always stored in affine form).
     *
     * Handles degenerate cases: p is infinity, or p equals/negates q.
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

        FieldP.sqr(t[0], p.z) // Z₁²
        FieldP.mul(t[1], t[0], p.z) // Z₁³
        FieldP.mul(t[2], qx, t[0]) // U₂ = qx·Z₁²  (U₁ = X₁ since Z₂=1)
        FieldP.mul(t[3], qy, t[1]) // S₂ = qy·Z₁³  (S₁ = Y₁ since Z₂=1)
        FieldP.sub(t[4], t[2], p.x) // H = U₂ - U₁

        if (U256.isZero(t[4])) {
            // Same x-coordinate: either same point (double) or inverse (infinity)
            val tmp = IntArray(8)
            FieldP.sub(tmp, t[3], p.y)
            if (U256.isZero(tmp)) doublePoint(out, p) else out.setInfinity()
            return
        }

        FieldP.add(t[5], t[4], t[4]) // 2H
        FieldP.sqr(t[5], t[5]) // I = (2H)²
        FieldP.mul(t[6], t[4], t[5]) // J = H·I
        FieldP.sub(t[7], t[3], p.y)
        FieldP.add(t[7], t[7], t[7]) // r = 2·(S₂ - S₁)
        FieldP.mul(t[8], p.x, t[5]) // V = U₁·I
        FieldP.sqr(out.x, t[7]) // X₃ = r²
        FieldP.sub(out.x, out.x, t[6]) //     - J
        FieldP.sub(out.x, out.x, t[8]) //     - V
        FieldP.sub(out.x, out.x, t[8]) //     - V
        FieldP.sub(t[9], t[8], out.x) // V - X₃
        FieldP.mul(out.y, t[7], t[9]) // Y₃ = r·(V-X₃)
        FieldP.mul(t[9], p.y, t[6]) //      - 2·S₁·J
        FieldP.add(t[9], t[9], t[9])
        FieldP.sub(out.y, out.y, t[9])
        FieldP.mul(out.z, p.z, t[4]) // Z₃ = 2·Z₁·H
        FieldP.add(out.z, out.z, out.z)
    }

    // ==================== Full Jacobian Addition (11M + 5S) ====================

    /**
     * General point addition: out = p + q, both in Jacobian coordinates.
     *
     * This is the most expensive addition variant because neither point has Z=1.
     * Used when adding points from on-the-fly tables (P multiples in verification).
     *
     * Handles degenerate cases: either point is infinity, or points are equal/inverse.
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

        FieldP.sqr(t[0], p.z) // Z₁²
        FieldP.sqr(t[1], q.z) // Z₂²
        FieldP.mul(t[2], p.x, t[1]) // U₁ = X₁·Z₂²
        FieldP.mul(t[3], q.x, t[0]) // U₂ = X₂·Z₁²
        FieldP.mul(t[4], q.z, t[1]) // Z₂³
        FieldP.mul(t[4], p.y, t[4]) // S₁ = Y₁·Z₂³
        FieldP.mul(t[5], p.z, t[0]) // Z₁³
        FieldP.mul(t[5], q.y, t[5]) // S₂ = Y₂·Z₁³

        if (U256.cmp(t[2], t[3]) == 0) {
            if (U256.cmp(t[4], t[5]) == 0) doublePoint(out, p) else out.setInfinity()
            return
        }

        FieldP.sub(t[6], t[3], t[2]) // H = U₂ - U₁
        FieldP.add(t[7], t[6], t[6])
        FieldP.sqr(t[7], t[7]) // I = (2H)²
        FieldP.mul(t[8], t[6], t[7]) // J = H·I
        FieldP.sub(t[9], t[5], t[4])
        FieldP.add(t[9], t[9], t[9]) // r = 2·(S₂-S₁)
        FieldP.mul(t[10], t[2], t[7]) // V = U₁·I

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
    // ==================== Scalar Multiplication ====================

    /**
     * General scalar multiplication: out = scalar · p.
     *
     * Uses GLV endomorphism + wNAF-5 to halve doublings from 256 to ~130:
     *   scalar = k₁ + k₂·λ (mod n), where k₁, k₂ are ~128 bits
     *   scalar·P = k₁·P + k₂·λ(P), with λ(P) = (β·X, Y, Z)
     *
     * The two ~128-bit half-scalars are wNAF-5 encoded and processed in a single
     * pass of ~130 shared doublings. P-side uses Jacobian tables (no inversions).
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

        val w = 5
        val tableSize = 1 shl (w - 2) // 8 entries

        // Split scalar via GLV: scalar = k₁ + k₂·λ
        val split = Glv.splitScalar(scalar)
        val wnaf1 = Glv.wnaf(split.k1, w, 129)
        val wnaf2 = Glv.wnaf(split.k2, w, 129)

        // P odd-multiples [1P, 3P, 5P, ..., 15P] via 2P stepping
        val p2 = MutablePoint()
        doublePoint(p2, p)
        val pOdd = Array(tableSize) { MutablePoint() }
        pOdd[0].copyFrom(p)
        for (i in 1 until tableSize) addPoints(pOdd[i], pOdd[i - 1], p2)

        // λ(P) odd-multiples: (β·X, Y, Z) in Jacobian
        val pLamOdd =
            Array(tableSize) { i ->
                val lp = MutablePoint()
                FieldP.mul(lp.x, pOdd[i].x, Glv.BETA)
                pOdd[i].y.copyInto(lp.y)
                pOdd[i].z.copyInto(lp.z)
                lp
            }

        // Find highest non-zero digit
        var bits = maxOf(wnaf1.size, wnaf2.size)
        while (bits > 0) {
            val b = bits - 1
            if ((b < wnaf1.size && wnaf1[b] != 0) || (b < wnaf2.size && wnaf2[b] != 0)) break
            bits--
        }

        out.setInfinity()
        val tmp = MutablePoint()
        val negJac = MutablePoint()

        for (i in bits - 1 downTo 0) {
            doublePoint(out, out)
            addWnafJacobian(out, tmp, negJac, wnaf1, i, pOdd, split.negK1)
            addWnafJacobian(out, tmp, negJac, wnaf2, i, pLamOdd, split.negK2)
        }
    }

    /**
     * Generator multiplication: out = scalar · G.
     *
     * Uses GLV endomorphism + wNAF-5 with precomputed affine G and λ(G) tables:
     *   scalar = k₁ + k₂·λ, then scalar·G = k₁·G + k₂·λ(G)
     * Both tables are cached (lazy static), so no per-call table building.
     * Mixed Jacobian+Affine addition (8M+3S) for all lookups.
     */
    fun mulG(
        out: MutablePoint,
        scalar: IntArray,
    ) {
        if (U256.isZero(scalar)) {
            out.setInfinity()
            return
        }

        val split = Glv.splitScalar(scalar)
        val wnaf1 = Glv.wnaf(split.k1, WINDOW_G, 129)
        val wnaf2 = Glv.wnaf(split.k2, WINDOW_G, 129)

        val gOdd = gOddTable
        val gLam = gLamTable

        var bits = maxOf(wnaf1.size, wnaf2.size)
        while (bits > 0) {
            val b = bits - 1
            if ((b < wnaf1.size && wnaf1[b] != 0) || (b < wnaf2.size && wnaf2[b] != 0)) break
            bits--
        }

        out.setInfinity()
        val tmp = MutablePoint()
        val negY = IntArray(8)

        for (i in bits - 1 downTo 0) {
            doublePoint(out, out)
            addWnafMixed(out, tmp, negY, wnaf1, i, gOdd, split.negK1)
            addWnafMixed(out, tmp, negY, wnaf2, i, gLam, split.negK2)
        }
    }

    /**
     * Shamir's trick with GLV endomorphism and wNAF-5:
     * out = s·G + e·P using 4 interleaved 128-bit scalar multiplications.
     *
     * GLV splits each 256-bit scalar into two ~128-bit halves:
     *   s = s₁ + s₂·λ,  e = e₁ + e₂·λ
     * Then: s·G + e·P = s₁·G + s₂·λ(G) + e₁·P + e₂·λ(P)
     * where λ(Q) = (β·Q.x, Q.y) is essentially free (one field multiply).
     *
     * The 4 half-scalars are wNAF-5 encoded and processed in a single pass of ~130
     * shared doublings (vs 256 without GLV). This roughly halves the verification cost.
     */
    fun mulDoubleG(
        out: MutablePoint,
        s: IntArray,
        p: MutablePoint,
        e: IntArray,
    ) {
        val wP = 5 // Window for P-side (table built per-call, keep small)
        val pTableSize = 1 shl (wP - 2) // 8 entries for P

        // Split scalars via GLV decomposition
        val sSplit = Glv.splitScalar(s)
        val eSplit = Glv.splitScalar(e)

        // Build wNAF: G-side uses wider window (cached table), P-side uses w=5
        val wnafS1 = Glv.wnaf(sSplit.k1, WINDOW_G, 129)
        val wnafS2 = Glv.wnaf(sSplit.k2, WINDOW_G, 129)
        val wnafE1 = Glv.wnaf(eSplit.k1, wP, 129)
        val wnafE2 = Glv.wnaf(eSplit.k2, wP, 129)

        // G tables: precomputed and cached (no per-verify allocation)
        val gOdd = gOddTable
        val gLam = gLamTable

        // P odd-multiples [1P, 3P, 5P, ..., 15P] via 2P stepping (1 double + 7 adds)
        val p2 = MutablePoint()
        doublePoint(p2, p)
        val pOdd = Array(pTableSize) { MutablePoint() }
        pOdd[0].copyFrom(p)
        for (i in 1 until pTableSize) addPoints(pOdd[i], pOdd[i - 1], p2)
        // λ(P) table: (β·X, Y, Z) in Jacobian — endomorphism preserves projective coords
        val pLamOdd =
            Array(pTableSize) { i ->
                val lp = MutablePoint()
                FieldP.mul(lp.x, pOdd[i].x, Glv.BETA)
                pOdd[i].y.copyInto(lp.y)
                pOdd[i].z.copyInto(lp.z)
                lp
            }

        // Find highest non-zero digit across all 4 streams
        val allWnaf = arrayOf(wnafS1, wnafS2, wnafE1, wnafE2)
        var bits = allWnaf.maxOf { it.size }
        while (bits > 0) {
            val b = bits - 1
            if (allWnaf.any { b < it.size && it[b] != 0 }) break
            bits--
        }

        out.setInfinity()
        val tmp = MutablePoint()
        val negY = IntArray(8)
        val negJac = MutablePoint() // Reused scratch for Jacobian negation

        for (i in bits - 1 downTo 0) {
            doublePoint(out, out)
            // Streams 1-2: G-side (affine tables, mixed addition)
            addWnafMixed(out, tmp, negY, wnafS1, i, gOdd, sSplit.negK1)
            addWnafMixed(out, tmp, negY, wnafS2, i, gLam, sSplit.negK2)
            // Streams 3-4: P-side (Jacobian tables, full addition)
            addWnafJacobian(out, tmp, negJac, wnafE1, i, pOdd, eSplit.negK1)
            addWnafJacobian(out, tmp, negJac, wnafE2, i, pLamOdd, eSplit.negK2)
        }
    }

    /**
     * Process one wNAF digit with mixed addition.
     * The effective sign is: (wNAF digit sign) XOR (GLV negation flag).
     * Positive = add as-is, negative = negate the table entry's y.
     */
    private fun addWnafMixed(
        out: MutablePoint,
        tmp: MutablePoint,
        negY: IntArray,
        wnafDigits: IntArray,
        bitIndex: Int,
        table: Array<AffinePoint>,
        glvNeg: Boolean,
    ) {
        if (bitIndex >= wnafDigits.size) return
        val d = wnafDigits[bitIndex]
        if (d == 0) return
        val idx = (if (d > 0) d else -d) / 2
        val effectiveNeg = (d < 0) xor glvNeg
        if (!effectiveNeg) {
            addMixed(tmp, out, table[idx].x, table[idx].y)
        } else {
            FieldP.neg(negY, table[idx].y)
            addMixed(tmp, out, table[idx].x, negY)
        }
        out.copyFrom(tmp)
    }

    /** Process one wNAF digit with full Jacobian addition (for P-side tables). */
    private fun addWnafJacobian(
        out: MutablePoint,
        tmp: MutablePoint,
        negScratch: MutablePoint,
        wnafDigits: IntArray,
        bitIndex: Int,
        table: Array<MutablePoint>,
        glvNeg: Boolean,
    ) {
        if (bitIndex >= wnafDigits.size) return
        val d = wnafDigits[bitIndex]
        if (d == 0) return
        val idx = (if (d > 0) d else -d) / 2
        val effectiveNeg = (d < 0) xor glvNeg
        if (!effectiveNeg) {
            addPoints(tmp, out, table[idx])
        } else {
            // Negate the Jacobian point: (X, -Y, Z) using pre-allocated scratch
            table[idx].x.copyInto(negScratch.x)
            FieldP.neg(negScratch.y, table[idx].y)
            table[idx].z.copyInto(negScratch.z)
            addPoints(tmp, out, negScratch)
        }
        out.copyFrom(tmp)
    }

    // ==================== Coordinate Conversion ====================

    /**
     * Convert from Jacobian (X, Y, Z) to affine (x, y) = (X/Z², Y/Z³).
     * Requires one field inversion (the most expensive single operation).
     * Returns false if the point is at infinity.
     */
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

    // ==================== Key Parsing and Serialization ====================

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

    /** Convenience: convert to affine and return as Pair, or null if infinity. */
    fun toAffinePair(p: MutablePoint): Pair<IntArray, IntArray>? {
        val x = IntArray(8)
        val y = IntArray(8)
        return if (toAffine(p, x, y)) Pair(x, y) else null
    }
}
