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
// POINT FORMULAS
// ==============
// - doublePoint: 3M+4S (uses fe_half for L=(3/2)·X², same as libsecp256k1)
// - addMixed (Jacobian + Affine): 8M+3S (used for precomputed table lookups)
// - addPoints (Jacobian + Jacobian): 11M+5S (used when both points are Jacobian)
//
// SCALAR MULTIPLICATION STRATEGIES
// ================================
// Three methods are used depending on the context:
//
// 1. mulG (Generator multiplication): Comb method (Hamburg 2012).
//    Arranges scalar bits into a 4×66 matrix, processes 4 rows with 11 table lookups
//    each. Only 3 doublings total. Uses a precomputed 704-entry affine table (~45KB).
//    Cost: ~43 mixed additions + 3 doublings ≈ 494 field ops.
//    Used by: pubkeyCreate, signSchnorr.
//
// 2. mul (Arbitrary point multiplication): GLV endomorphism + wNAF-5 (Glv.kt).
//    Splits the 256-bit scalar into two ~128-bit halves via the secp256k1 endomorphism,
//    then processes both with wNAF encoding in a single pass of ~130 shared doublings.
//    P-side tables are batch-inverted to affine (effective-affine technique) so the
//    main loop uses addMixed (8M+3S) instead of addPoints (11M+5S), saving ~4M per add.
//    Used by: pubKeyTweakMul (ECDH), ecdhXOnly.
//
// 3. mulDoubleG (Verification: s·G + e·P): Strauss/Shamir trick with GLV + wNAF.
//    Splits both scalars via GLV into 4 half-scalar streams. G-side uses a precomputed
//    1024-entry affine wNAF-12 table (~128KB); P-side tables batch-inverted to affine.
//    All 4 streams share ~130 doublings with mixed additions throughout.
//    Used by: verifySchnorr.
//
// BATCH INVERSION
// ===============
// Montgomery's trick: convert n Jacobian→affine with 1 inversion + 3(n-1) muls instead
// of n individual inversions. Used for wNAF table construction and G table initialization.
//
// PRECOMPUTED TABLES
// ==================
// All tables are lazily initialized on first use (Kotlin `by lazy`):
// - combTable: 704 affine points for mulG (~45KB, built once per process)
// - gOddTable: 1024 affine points for G-side wNAF-12 (~128KB, batch-inverted)
// - gLamTable: 1024 affine points for λ(G)-side wNAF-12 (~128KB, derived from gOddTable)
//
// Note: C libsecp256k1 uses WINDOW_G=15 (8192 entries, 1MB) as compile-time .rodata.
// On JVM, w=15 is slower due to cache pressure from heap-allocated AffinePoint objects.
// w=12 (1024 entries, ~128KB) is the sweet spot — fits in L2, fewer additions than w=8.
// =====================================================================================

/**
 * Mutable Jacobian point for in-place computation.
 *
 * Points are mutable to avoid allocating new objects during the inner loop of scalar
 * multiplication, which performs thousands of doublings and additions per operation.
 */
internal class MutablePoint(
    val x: LongArray = LongArray(4),
    val y: LongArray = LongArray(4),
    val z: LongArray = LongArray(4),
) {
    fun isInfinity(): Boolean = U256.isZero(z)

    fun setInfinity() {
        for (i in 0 until 4) {
            x[i] = 0L
            z[i] = 0L
        }
        y[0] = 1L
        for (i in 1 until 4) y[i] = 0L
    }

    fun copyFrom(other: MutablePoint) {
        other.x.copyInto(x)
        other.y.copyInto(y)
        other.z.copyInto(z)
    }

    fun setAffine(
        ax: LongArray,
        ay: LongArray,
    ) {
        ax.copyInto(x)
        ay.copyInto(y)
        z[0] = 1L
        for (i in 1 until 4) z[i] = 0L
    }
}

/**
 * Affine point (x, y) — no Z coordinate.
 * Used for precomputed tables where we want compact storage and mixed addition.
 */
internal class AffinePoint(
    val x: LongArray = LongArray(4),
    val y: LongArray = LongArray(4),
)

internal object ECPoint {
    // ==================== Generator point G ====================

    val GX =
        longArrayOf(
            6481385041966929816L,
            188021827762530521L,
            6170039885052185351L,
            8772561819708210092L,
        )
    val GY =
        longArrayOf(
            -7185545363635252040L,
            -209500633525038055L,
            6747795201694173352L,
            5204712524664259685L,
        )

    /** Curve constant b = 7 in y² = x³ + 7. */
    private val B = longArrayOf(7L, 0L, 0L, 0L)

    /**
     * wNAF window width for the G-side of scalar multiplication (mulDoubleG/verify).
     * Width w uses a table of 2^(w-2) odd multiples. Larger windows mean fewer
     * additions but more precomputed storage (lazily allocated on first use):
     *   w=8:  64 entries,  ~16 adds per 128-bit scalar (~8KB table)
     *   w=10: 256 entries, ~13 adds per 128-bit scalar (~32KB table)
     *   w=12: 1024 entries,~11 adds per 128-bit scalar (~128KB table)
     *   w=14: 4096 entries,~9 adds per 128-bit scalar (~512KB table)
     * C libsecp256k1 uses w=15 (8192 entries, 1MB) precomputed at compile time.
     * w=12 is a good tradeoff for runtime-computed tables on JVM.
     */
    private const val WINDOW_G = 12
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

        return batchToAffine(jac)
    }

    /**
     * Convert an array of Jacobian points to affine using Montgomery's batch inversion trick.
     * Cost: 1 field inversion + 3(n-1) multiplications, instead of n inversions.
     * This is critical for large precomputed tables (e.g., 1024 entries for WINDOW_G=12).
     */
    internal fun batchToAffine(jac: Array<MutablePoint>): Array<AffinePoint> {
        val n = jac.size
        if (n == 0) return emptyArray()

        // Step 1: compute prefix products of Z coordinates
        // prods[i] = z[0] * z[1] * ... * z[i]
        val prods = Array(n) { LongArray(4) }
        jac[0].z.copyInto(prods[0])
        for (i in 1 until n) {
            FieldP.mul(prods[i], prods[i - 1], jac[i].z)
        }

        // Step 2: invert the total product
        val inv = LongArray(4)
        FieldP.inv(inv, prods[n - 1])

        // Step 3: recover individual inverses by multiplying back
        // zInv[i] = product_inv * prods[i-1] = 1 / z[i]
        val zInv = LongArray(4)
        val zInv2 = LongArray(4)
        val zInv3 = LongArray(4)
        val result = Array(n) { AffinePoint() }

        for (i in n - 1 downTo 1) {
            // zInv = inv * prods[i-1] = 1/z[i]
            FieldP.mul(zInv, inv, prods[i - 1])
            // Update inv for next iteration: inv = inv * z[i] = 1/(z[0]*...*z[i-1])
            FieldP.mul(inv, inv, jac[i].z)
            // Convert to affine: x' = X/Z², y' = Y/Z³
            FieldP.sqr(zInv2, zInv)
            FieldP.mul(zInv3, zInv2, zInv)
            FieldP.mul(result[i].x, jac[i].x, zInv2)
            FieldP.mul(result[i].y, jac[i].y, zInv3)
        }
        // i=0: inv is now 1/z[0]
        FieldP.sqr(zInv2, inv)
        FieldP.mul(zInv3, zInv2, inv)
        FieldP.mul(result[0].x, jac[0].x, zInv2)
        FieldP.mul(result[0].y, jac[0].y, zInv3)

        return result
    }

    // ==================== Comb Method for G Multiplication ====================
    //
    // The comb method (Hamburg 2012) is much faster than windowed scalar multiplication
    // for a fixed base point because it avoids most doublings. Instead of processing the
    // scalar bit-by-bit with doublings between each, it arranges the scalar bits into a
    // 2D matrix (SPACING rows × BLOCKS*TEETH columns) and processes each row with table
    // lookups. Only SPACING-1 doublings are needed between rows.
    //
    // With BLOCKS=11, TEETH=6, SPACING=4:
    //   - Doublings: 3 (vs ~130 for GLV+wNAF)
    //   - Additions: ~43 mixed (11 blocks × ~98% non-zero × 4 rows)
    //   - Total: ~43 mixed additions + 3 doublings ≈ 464 M-equiv
    //   - vs GLV+wNAF: ~130 doublings + ~32 additions ≈ 1,035 M-equiv (2.2× faster)
    //
    // The table has 11 blocks × 64 entries = 704 affine points (~45KB), lazily computed.

    private const val COMB_BLOCKS = 11
    private const val COMB_TEETH = 6
    private const val COMB_SPACING = 4
    private const val COMB_POINTS = 1 shl COMB_TEETH // 64

    private val combTable: Array<AffinePoint> by lazy { buildCombTable() }

    private fun buildCombTable(): Array<AffinePoint> {
        // Tooth base points: toothG[i] = 2^(i * SPACING) * G
        val numTeeth = COMB_BLOCKS * COMB_TEETH
        val toothG = Array(numTeeth) { MutablePoint() }
        toothG[0].setAffine(GX, GY)
        for (i in 1 until numTeeth) {
            toothG[i].copyFrom(toothG[i - 1])
            repeat(COMB_SPACING) { doublePoint(toothG[i], toothG[i]) }
        }

        // For each block, build all 2^TEETH combinations of its teeth
        val tableSize = COMB_BLOCKS * COMB_POINTS
        val jac = Array(tableSize) { MutablePoint() }
        val tmp = MutablePoint()

        for (b in 0 until COMB_BLOCKS) {
            val base = b * COMB_POINTS
            jac[base].setInfinity()
            for (m in 1 until COMB_POINTS) {
                val changedBit = Integer.numberOfTrailingZeros(m)
                if (m and (m - 1) == 0) {
                    jac[base + m].copyFrom(toothG[b * COMB_TEETH + changedBit])
                } else {
                    val prev = m xor (1 shl changedBit)
                    addPoints(tmp, jac[base + prev], toothG[b * COMB_TEETH + changedBit])
                    jac[base + m].copyFrom(tmp)
                }
            }
        }

        return Array(tableSize) { i ->
            if (jac[i].isInfinity()) {
                AffinePoint(LongArray(4), LongArray(4))
            } else {
                val x = LongArray(4)
                val y = LongArray(4)
                toAffine(jac[i], x, y)
                AffinePoint(x, y)
            }
        }
    }

    // ==================== Thread-local scratch buffers ====================

    /**
     * Scratch space for point operations. Each thread gets its own set of temporary
     * field elements and a wide buffer to avoid allocation and ThreadLocal lookups
     * in the inner loops. The 12 temp buffers (t[0]..t[11]) are shared across
     * doublePoint and addPoints — this is safe because these functions only call
     * each other in the equal-point degenerate case, which returns immediately
     * after the recursive call without using the temps further.
     *
     * The wide buffer (LongArray(8)) is pre-fetched once per top-level operation
     * and passed through to FieldP.mul/sqr, avoiding ~500+ ThreadLocal.get() calls
     * per scalar multiplication (~20-30ns each on JVM).
     */
    internal class PointScratch {
        val t = Array(12) { LongArray(4) }
        val dblCopy = MutablePoint() // Copy buffer for in-place doubling (out === input)
        val w = LongArray(8) // Wide buffer for FieldP.mul/sqr — shared, avoids ThreadLocal

        // Pre-allocated scratch for wNAF encoding (avoids IntArray allocation per call).
        // Size 145 = 129 (max bits after GLV split) + 15 (max window) + 1 (headroom).
        val wnaf1 = IntArray(145)
        val wnaf2 = IntArray(145)
        val wnaf3 = IntArray(145) // mulDoubleG needs 4 wNAF arrays
        val wnaf4 = IntArray(145)
        val wnafTmp = LongArray(4) // scratch for wnaf scalar copy (GLV scalars are up to 4 limbs)

        // Pre-allocated scratch for wNAF mixed addition
        val mixTmp = MutablePoint()
        val mixNegY = LongArray(4)

        // Pre-allocated P-side tables for mul/mulDoubleG (avoids ~80 LongArray allocs per call)
        val pOddJac = Array(8) { MutablePoint() }
        val pLamOddJac = Array(8) { MutablePoint() }
        val pOddAff = Array(8) { AffinePoint() }
        val pLamOddAff = Array(8) { AffinePoint() }
        val p2 = MutablePoint() // doublePoint temp for table building

        // Pre-allocated batch inversion temps (avoids 12 LongArray allocs per call)
        val cumZ = Array(8) { LongArray(4) }
        val batchInv = LongArray(4)
        val batchZInv = LongArray(4)
        val batchZInv2 = LongArray(4)
        val batchZInv3 = LongArray(4)
    }

    private val scratch = ThreadLocal.withInitial { PointScratch() }

    /** Get thread-local scratch. Call once at the top-level entry point. */
    internal fun getScratch(): PointScratch = scratch.get()

    // ==================== Point Doubling (3M + 4S) ====================

    // Point doubling: out = 2·p.
    //
    // Uses the optimized formula from libsecp256k1 for curves with a=0:
    //   S = Y², L = (3/2)·X², T = -X·S
    //   X₃ = L² + 2T,  Y₃ = -(L·(X₃+T) + S²),  Z₃ = Y·Z
    //
    // Cost: 3 multiplications + 4 squarings + field halving + adds/negations.
    // Safe for out === inp (in-place doubling) via internal copy buffer.

    // doublePoint with ThreadLocal scratch (convenience for non-hot paths).
    fun doublePoint(
        out: MutablePoint,
        inp: MutablePoint,
    ) = doublePoint(out, inp, scratch.get())

    /** doublePoint with caller-provided scratch (hot path — no ThreadLocal lookup). */
    fun doublePoint(
        out: MutablePoint,
        inp: MutablePoint,
        s: PointScratch,
    ) {
        if (inp.isInfinity()) {
            out.setInfinity()
            return
        }
        val p =
            if (out === inp) {
                s.dblCopy.copyFrom(inp)
                s.dblCopy
            } else {
                inp
            }
        val t = s.t
        val w = s.w

        FieldP.sqr(t[0], p.y, w) // S = Y²
        FieldP.sqr(t[1], p.x, w) // X²
        FieldP.add(t[2], t[1], t[1]) // 2·X²
        FieldP.add(t[2], t[2], t[1]) // 3·X²
        FieldP.half(t[2], t[2]) // L = (3/2)·X²
        FieldP.mul(t[3], p.x, t[0], w) // X·S
        FieldP.neg(t[3], t[3]) // T = -X·S
        FieldP.sqr(out.x, t[2], w) // X₃ = L²
        FieldP.add(out.x, out.x, t[3]) //     + T
        FieldP.add(out.x, out.x, t[3]) //     + T
        FieldP.add(t[4], out.x, t[3]) // X₃ + T
        FieldP.mul(t[4], t[2], t[4], w) // L·(X₃+T)
        FieldP.sqr(t[5], t[0], w) // S²
        FieldP.add(t[4], t[4], t[5]) // L·(X₃+T) + S²
        FieldP.neg(out.y, t[4]) // Y₃ = negate
        FieldP.mul(out.z, p.y, p.z, w) // Z₃ = Y·Z
    }

    // ==================== Mixed Addition: Jacobian + Affine (8M + 3S) ====================

    // Mixed point addition: out = p + (qx, qy) where q is an affine point (Z=1).
    // Cost: 8M + 3S. Saves 4M+1S vs full Jacobian by exploiting Z₂=1.

    // addMixed with ThreadLocal scratch (convenience for non-hot paths).
    fun addMixed(
        out: MutablePoint,
        p: MutablePoint,
        qx: LongArray,
        qy: LongArray,
    ) = addMixed(out, p, qx, qy, scratch.get())

    /** addMixed with caller-provided scratch (hot path — no ThreadLocal lookup). */
    fun addMixed(
        out: MutablePoint,
        p: MutablePoint,
        qx: LongArray,
        qy: LongArray,
        s: PointScratch,
    ) {
        if (p.isInfinity()) {
            out.setAffine(qx, qy)
            return
        }
        val t = s.t
        val w = s.w

        FieldP.sqr(t[0], p.z, w) // Z₁²
        FieldP.mul(t[1], t[0], p.z, w) // Z₁³
        FieldP.mul(t[2], qx, t[0], w) // U₂ = qx·Z₁²  (U₁ = X₁ since Z₂=1)
        FieldP.mul(t[3], qy, t[1], w) // S₂ = qy·Z₁³  (S₁ = Y₁ since Z₂=1)
        FieldP.sub(t[4], t[2], p.x) // H = U₂ - U₁

        if (U256.isZero(t[4])) {
            FieldP.sub(t[5], t[3], p.y)
            if (U256.isZero(t[5])) doublePoint(out, p, s) else out.setInfinity()
            return
        }

        FieldP.add(t[5], t[4], t[4]) // 2H
        FieldP.sqr(t[5], t[5], w) // I = (2H)²
        FieldP.mul(t[6], t[4], t[5], w) // J = H·I
        FieldP.sub(t[7], t[3], p.y)
        FieldP.add(t[7], t[7], t[7]) // r = 2·(S₂ - S₁)
        FieldP.mul(t[8], p.x, t[5], w) // V = U₁·I
        FieldP.sqr(out.x, t[7], w) // X₃ = r²
        FieldP.sub(out.x, out.x, t[6]) //     - J
        FieldP.sub(out.x, out.x, t[8]) //     - V
        FieldP.sub(out.x, out.x, t[8]) //     - V
        FieldP.sub(t[9], t[8], out.x) // V - X₃
        FieldP.mul(out.y, t[7], t[9], w) // Y₃ = r·(V-X₃)
        FieldP.mul(t[9], p.y, t[6], w) //      - 2·S₁·J
        FieldP.add(t[9], t[9], t[9])
        FieldP.sub(out.y, out.y, t[9])
        FieldP.mul(out.z, p.z, t[4], w) // Z₃ = 2·Z₁·H
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
    ) = addPoints(out, p, q, scratch.get())

    fun addPoints(
        out: MutablePoint,
        p: MutablePoint,
        q: MutablePoint,
        s: PointScratch,
    ) {
        if (p.isInfinity()) {
            out.copyFrom(q)
            return
        }
        if (q.isInfinity()) {
            out.copyFrom(p)
            return
        }
        val t = s.t
        val w = s.w

        FieldP.sqr(t[0], p.z, w) // Z₁²
        FieldP.sqr(t[1], q.z, w) // Z₂²
        FieldP.mul(t[2], p.x, t[1], w) // U₁ = X₁·Z₂²
        FieldP.mul(t[3], q.x, t[0], w) // U₂ = X₂·Z₁²
        FieldP.mul(t[4], q.z, t[1], w) // Z₂³
        FieldP.mul(t[4], p.y, t[4], w) // S₁ = Y₁·Z₂³
        FieldP.mul(t[5], p.z, t[0], w) // Z₁³
        FieldP.mul(t[5], q.y, t[5], w) // S₂ = Y₂·Z₁³

        if (U256.cmp(t[2], t[3]) == 0) {
            if (U256.cmp(t[4], t[5]) == 0) doublePoint(out, p, s) else out.setInfinity()
            return
        }

        FieldP.sub(t[6], t[3], t[2]) // H = U₂ - U₁
        FieldP.add(t[7], t[6], t[6])
        FieldP.sqr(t[7], t[7], w) // I = (2H)²
        FieldP.mul(t[8], t[6], t[7], w) // J = H·I
        FieldP.sub(t[9], t[5], t[4])
        FieldP.add(t[9], t[9], t[9]) // r = 2·(S₂-S₁)
        FieldP.mul(t[10], t[2], t[7], w) // V = U₁·I

        FieldP.sqr(out.x, t[9], w)
        FieldP.sub(out.x, out.x, t[8])
        FieldP.sub(out.x, out.x, t[10])
        FieldP.sub(out.x, out.x, t[10])
        FieldP.sub(t[11], t[10], out.x)
        FieldP.mul(out.y, t[9], t[11], w)
        FieldP.mul(t[11], t[4], t[8], w)
        FieldP.add(t[11], t[11], t[11])
        FieldP.sub(out.y, out.y, t[11])
        FieldP.add(out.z, p.z, q.z)
        FieldP.sqr(out.z, out.z, w)
        FieldP.sub(out.z, out.z, t[0])
        FieldP.sub(out.z, out.z, t[1])
        FieldP.mul(out.z, out.z, t[6], w)
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
        scalar: LongArray,
    ) {
        if (U256.isZero(scalar) || p.isInfinity()) {
            out.setInfinity()
            return
        }

        val s = scratch.get()
        val wnd = 5
        val tableSize = 1 shl (wnd - 2) // 8 entries

        // Split scalar via GLV: scalar = k₁ + k₂·λ
        val split = Glv.splitScalar(scalar)
        Glv.wnafInto(s.wnaf1, s.wnafTmp, split.k1, wnd, 129)
        Glv.wnafInto(s.wnaf2, s.wnafTmp, split.k2, wnd, 129)
        val wnaf1 = s.wnaf1
        val wnaf2 = s.wnaf2

        // P odd-multiples [1P, 3P, 5P, ..., 15P] via 2P stepping (Jacobian)
        // Uses pre-allocated tables from PointScratch to avoid ~80 LongArray allocs
        doublePoint(s.p2, p, s)
        val pOddJac = s.pOddJac
        pOddJac[0].copyFrom(p)
        for (i in 1 until tableSize) addPoints(pOddJac[i], pOddJac[i - 1], s.p2, s)

        // λ(P) odd-multiples: (β·X, Y, Z) — Z is identical to pOddJac
        val pLamOddJac = s.pLamOddJac
        for (i in 0 until tableSize) {
            FieldP.mul(pLamOddJac[i].x, pOddJac[i].x, Glv.BETA, s.w)
            pOddJac[i].y.copyInto(pLamOddJac[i].y)
            pOddJac[i].z.copyInto(pLamOddJac[i].z)
        }

        // Effective-affine: batch-convert with shared Z inversion
        val pOdd = s.pOddAff
        val pLamOdd = s.pLamOddAff
        batchToAffinePair(pOddJac, pLamOddJac, pOdd, pLamOdd, s)

        // Find highest non-zero digit
        var bits = 129 + wnd
        while (bits > 0 && wnaf1[bits - 1] == 0 && wnaf2[bits - 1] == 0) {
            bits--
        }

        out.setInfinity()
        val tmp = s.mixTmp
        val negY = s.mixNegY

        for (i in bits - 1 downTo 0) {
            doublePoint(out, out, s)
            addWnafMixed(out, tmp, negY, wnaf1, i, pOdd, split.negK1, s)
            addWnafMixed(out, tmp, negY, wnaf2, i, pLamOdd, split.negK2, s)
        }
    }

    /**
     * Generator multiplication using the comb method: out = scalar · G.
     *
     * Arranges the 256 scalar bits into a 2D matrix (4 rows × 66 columns) and
     * processes each row with 11 table lookups (one per block of 6 bits).
     * Only 3 doublings are needed between rows, vs ~130 for GLV+wNAF.
     *
     * Total: ~43 mixed additions + 3 doublings ≈ 464 M-equiv
     * (vs ~1,035 for the previous GLV+wNAF approach — 2.2× faster)
     */
    fun mulG(
        out: MutablePoint,
        scalar: LongArray,
    ) {
        if (U256.isZero(scalar)) {
            out.setInfinity()
            return
        }

        val s = scratch.get()
        val table = combTable
        out.setInfinity()
        val tmp = MutablePoint()

        for (combOff in COMB_SPACING - 1 downTo 0) {
            if (combOff < COMB_SPACING - 1) {
                doublePoint(out, out, s)
            }
            for (block in 0 until COMB_BLOCKS) {
                var mask = 0
                for (tooth in 0 until COMB_TEETH) {
                    val bitPos = (block * COMB_TEETH + tooth) * COMB_SPACING + combOff
                    if (bitPos < 256 && U256.testBit(scalar, bitPos)) {
                        mask = mask or (1 shl tooth)
                    }
                }
                if (mask != 0) {
                    val entry = table[block * COMB_POINTS + mask]
                    addMixed(tmp, out, entry.x, entry.y, s)
                    out.copyFrom(tmp)
                }
            }
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
        s: LongArray,
        p: MutablePoint,
        e: LongArray,
    ) {
        val sc = scratch.get()
        val wP = 5 // Window for P-side (table built per-call, keep small)
        val pTableSize = 1 shl (wP - 2) // 8 entries for P

        // Split scalars via GLV decomposition
        val sSplit = Glv.splitScalar(s)
        val eSplit = Glv.splitScalar(e)

        // Build wNAF: G-side uses wider window (cached table), P-side uses w=5
        Glv.wnafInto(sc.wnaf1, sc.wnafTmp, sSplit.k1, WINDOW_G, 129)
        Glv.wnafInto(sc.wnaf2, sc.wnafTmp, sSplit.k2, WINDOW_G, 129)
        Glv.wnafInto(sc.wnaf3, sc.wnafTmp, eSplit.k1, wP, 129)
        Glv.wnafInto(sc.wnaf4, sc.wnafTmp, eSplit.k2, wP, 129)
        val wnafS1 = sc.wnaf1
        val wnafS2 = sc.wnaf2
        val wnafE1 = sc.wnaf3
        val wnafE2 = sc.wnaf4

        // G tables: precomputed and cached (no per-verify allocation)
        val gOdd = gOddTable
        val gLam = gLamTable

        // P odd-multiples [1P, 3P, 5P, ..., 15P] — uses pre-allocated scratch tables
        doublePoint(sc.p2, p, sc)
        val pOddJac = sc.pOddJac
        pOddJac[0].copyFrom(p)
        for (i in 1 until pTableSize) addPoints(pOddJac[i], pOddJac[i - 1], sc.p2, sc)
        val pLamOddJac = sc.pLamOddJac
        for (i in 0 until pTableSize) {
            FieldP.mul(pLamOddJac[i].x, pOddJac[i].x, Glv.BETA, sc.w)
            pOddJac[i].y.copyInto(pLamOddJac[i].y)
            pOddJac[i].z.copyInto(pLamOddJac[i].z)
        }

        // Effective-affine: batch-convert P-side tables (shared Z inversion)
        val pOdd = sc.pOddAff
        val pLamOdd = sc.pLamOddAff
        batchToAffinePair(pOddJac, pLamOddJac, pOdd, pLamOdd, sc)

        // Find highest non-zero digit across all 4 streams
        var bits = 129 + WINDOW_G // max possible wNAF length
        while (bits > 0 && wnafS1[bits - 1] == 0 && wnafS2[bits - 1] == 0 &&
            wnafE1[bits - 1] == 0 && wnafE2[bits - 1] == 0
        ) {
            bits--
        }

        out.setInfinity()
        val tmp = sc.mixTmp
        val negY = sc.mixNegY

        for (i in bits - 1 downTo 0) {
            doublePoint(out, out, sc)
            // Streams 1-2: G-side (affine tables, mixed addition)
            addWnafMixed(out, tmp, negY, wnafS1, i, gOdd, sSplit.negK1, sc)
            addWnafMixed(out, tmp, negY, wnafS2, i, gLam, sSplit.negK2, sc)
            // Streams 3-4: P-side (affine tables via effective-affine, mixed addition)
            addWnafMixed(out, tmp, negY, wnafE1, i, pOdd, eSplit.negK1, sc)
            addWnafMixed(out, tmp, negY, wnafE2, i, pLamOdd, eSplit.negK2, sc)
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
        negY: LongArray,
        wnafDigits: IntArray,
        bitIndex: Int,
        table: Array<AffinePoint>,
        glvNeg: Boolean,
        s: PointScratch,
    ) {
        if (bitIndex >= wnafDigits.size) return
        val d = wnafDigits[bitIndex]
        if (d == 0) return
        val idx = (if (d > 0) d else -d) / 2
        val effectiveNeg = (d < 0) xor glvNeg
        if (!effectiveNeg) {
            addMixed(tmp, out, table[idx].x, table[idx].y, s)
        } else {
            FieldP.neg(negY, table[idx].y)
            addMixed(tmp, out, table[idx].x, negY, s)
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
        s: PointScratch,
    ) {
        if (bitIndex >= wnafDigits.size) return
        val d = wnafDigits[bitIndex]
        if (d == 0) return
        val idx = (if (d > 0) d else -d) / 2
        val effectiveNeg = (d < 0) xor glvNeg
        if (!effectiveNeg) {
            addPoints(tmp, out, table[idx], s)
        } else {
            table[idx].x.copyInto(negScratch.x)
            FieldP.neg(negScratch.y, table[idx].y)
            table[idx].z.copyInto(negScratch.z)
            addPoints(tmp, out, negScratch, s)
        }
        out.copyFrom(tmp)
    }

    // ==================== Batch Affine Conversion (Montgomery's Trick) ====================

    /**
     * Convert an array of Jacobian points to affine using Montgomery's batch inversion trick.
     *
     * Instead of n separate inversions (each ~250 multiplications), this computes all n inverses
     * with a single inversion + 3(n-1) multiplications:
     *   1. Build prefix products: c[i] = Z[0] · Z[1] · … · Z[i]
     *   2. Invert the final product: inv = c[n-1]⁻¹
     *   3. Recover individual inverses by peeling off factors from right to left:
     *        Z[i]⁻¹ = inv · c[i-1],  then  inv ← inv · Z[i]
     *   4. Convert each point: x' = X · (Z⁻¹)², y' = Y · (Z⁻¹)³
     *
     * Cost: 1 inversion + 3(n-1) muls + 2n muls (for x,y conversion) = 1 inv + (5n-3) muls
     * vs n inversions ≈ 250n muls.
     */
    private fun batchToAffine(
        points: Array<MutablePoint>,
        s: PointScratch,
    ): Array<AffinePoint> {
        val n = points.size
        if (n == 0) return emptyArray()

        val w = s.w

        // Prefix products of Z coordinates
        val cumZ = Array(n) { LongArray(4) }
        U256.copyInto(cumZ[0], points[0].z)
        for (i in 1 until n) {
            FieldP.mul(cumZ[i], cumZ[i - 1], points[i].z, w)
        }

        // Invert the total product
        val inv = LongArray(4)
        FieldP.inv(inv, cumZ[n - 1])

        // Recover individual Z inverses and convert to affine
        val result = Array(n) { AffinePoint() }
        val zInv = LongArray(4)
        val zInv2 = LongArray(4)
        val zInv3 = LongArray(4)

        for (i in n - 1 downTo 1) {
            // zInv = inv * cumZ[i-1] gives Z[i]^{-1}
            FieldP.mul(zInv, inv, cumZ[i - 1], w)
            // Update inv: inv = inv * Z[i] gives product-inverse without Z[i]
            FieldP.mul(inv, inv, points[i].z, w)
            // Convert to affine
            FieldP.sqr(zInv2, zInv, w)
            FieldP.mul(zInv3, zInv2, zInv, w)
            FieldP.mul(result[i].x, points[i].x, zInv2, w)
            FieldP.mul(result[i].y, points[i].y, zInv3, w)
        }
        // i == 0: inv now holds Z[0]^{-1}
        FieldP.sqr(zInv2, inv, w)
        FieldP.mul(zInv3, zInv2, inv, w)
        FieldP.mul(result[0].x, points[0].x, zInv2, w)
        FieldP.mul(result[0].y, points[0].y, zInv3, w)

        return result
    }

    /**
     * Batch-convert a pair of Jacobian tables that share the same Z coordinates.
     * This is the common case for GLV: pOdd and pLamOdd = (β·X, Y, Z) have identical Z.
     * Uses a single batch inversion for both tables, saving ~270 field ops (one full inv).
     */
    private fun batchToAffinePair(
        a: Array<MutablePoint>,
        b: Array<MutablePoint>,
        outA: Array<AffinePoint>,
        outB: Array<AffinePoint>,
        s: PointScratch,
    ) {
        val n = a.size
        if (n == 0) return
        val w = s.w

        // Build prefix products of Z (shared between a and b)
        val cumZ = s.cumZ
        a[0].z.copyInto(cumZ[0])
        for (i in 1 until n) {
            FieldP.mul(cumZ[i], cumZ[i - 1], a[i].z, w)
        }

        // Single inversion of the total product
        val inv = s.batchInv
        FieldP.inv(inv, cumZ[n - 1])

        // Recover individual Z⁻¹ and convert both tables
        val zInv = s.batchZInv
        val zInv2 = s.batchZInv2
        val zInv3 = s.batchZInv3

        for (i in n - 1 downTo 1) {
            FieldP.mul(zInv, inv, cumZ[i - 1], w)
            FieldP.mul(inv, inv, a[i].z, w)
            FieldP.sqr(zInv2, zInv, w)
            FieldP.mul(zInv3, zInv2, zInv, w)
            // Convert a[i]
            FieldP.mul(outA[i].x, a[i].x, zInv2, w)
            FieldP.mul(outA[i].y, a[i].y, zInv3, w)
            // Convert b[i] — same zInv since b has same Z
            FieldP.mul(outB[i].x, b[i].x, zInv2, w)
            FieldP.mul(outB[i].y, b[i].y, zInv3, w)
        }
        // i == 0
        FieldP.sqr(zInv2, inv, w)
        FieldP.mul(zInv3, zInv2, inv, w)
        FieldP.mul(outA[0].x, a[0].x, zInv2, w)
        FieldP.mul(outA[0].y, a[0].y, zInv3, w)
        FieldP.mul(outB[0].x, b[0].x, zInv2, w)
        FieldP.mul(outB[0].y, b[0].y, zInv3, w)
    }

    // ==================== Coordinate Conversion ====================

    /**
     * Convert from Jacobian (X, Y, Z) to affine (x, y) = (X/Z², Y/Z³).
     * Requires one field inversion (the most expensive single operation).
     * Returns false if the point is at infinity.
     */
    fun toAffine(
        p: MutablePoint,
        outX: LongArray,
        outY: LongArray,
    ): Boolean {
        if (p.isInfinity()) return false
        val zInv = LongArray(4)
        val zInv2 = LongArray(4)
        val zInv3 = LongArray(4)
        FieldP.inv(zInv, p.z)
        FieldP.sqr(zInv2, zInv)
        FieldP.mul(zInv3, zInv2, zInv)
        FieldP.mul(outX, p.x, zInv2)
        FieldP.mul(outY, p.y, zInv3)
        return true
    }

    /**
     * Convert from Jacobian to affine, returning only the x-coordinate: x = X/Z².
     * Saves 2 multiplications vs full toAffine (no zInv3, no outY computation).
     * Used by ecdhXOnly where only the x-coordinate of the shared point is needed.
     */
    fun toAffineX(
        p: MutablePoint,
        outX: LongArray,
    ): Boolean {
        if (p.isInfinity()) return false
        val zInv = LongArray(4)
        val zInv2 = LongArray(4)
        FieldP.inv(zInv, p.z)
        FieldP.sqr(zInv2, zInv)
        FieldP.mul(outX, p.x, zInv2)
        return true
    }

    // ==================== Key Encoding (delegates to KeyCodec) ====================

    fun liftX(
        outX: LongArray,
        outY: LongArray,
        x: LongArray,
    ) = KeyCodec.liftX(outX, outY, x)

    fun hasEvenY(y: LongArray) = KeyCodec.hasEvenY(y)

    fun parsePublicKey(
        pubkey: ByteArray,
        outX: LongArray,
        outY: LongArray,
    ) = KeyCodec.parsePublicKey(pubkey, outX, outY)

    fun serializeUncompressed(
        x: LongArray,
        y: LongArray,
    ) = KeyCodec.serializeUncompressed(x, y)

    fun serializeCompressed(
        x: LongArray,
        y: LongArray,
    ) = KeyCodec.serializeCompressed(x, y)
}
