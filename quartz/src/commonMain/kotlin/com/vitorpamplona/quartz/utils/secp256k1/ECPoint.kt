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
// Point arithmetic on the secp256k1 curve: y² = x³ + 7 (mod p).
// See PointTypes.kt for MutablePoint, AffinePoint, and PointScratch.
//
// POINT FORMULAS
// ==============
// - doublePoint: 3M+4S (uses fe_half for L=(3/2)·X², same as libsecp256k1)
// - addMixed (Jacobian + Affine): 8M+3S (precomputed table lookups)
// - addPoints (Jacobian + Jacobian): 11M+5S (both points Jacobian)
//
// SCALAR MULTIPLICATION
// =====================
// 1. mulG (Generator): Comb method, only 3 doublings + ~43 table lookups.
// 2. mul (Arbitrary): GLV + wNAF-5, ~130 shared doublings.
// 3. mulDoubleG (Verify: s·G + e·P): Strauss/Shamir + GLV + wNAF, 4 streams.
//
// PRECOMPUTED TABLES (lazily initialized)
// =======================================
// - combTable: 704 affine points for mulG (~45KB)
// - gOddTable: 1024 affine points for G-side wNAF-12 (~128KB)
// - gLamTable: 1024 affine points for λ(G)-side wNAF-12 (~128KB)
// - pTableCache: 256-entry cache of P-side wNAF tables (~256KB, for verify)
// =====================================================================================

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

    // Thread-local scratch — declared before precomputed tables because
    // buildGOddTable() and buildCombTable() use doublePoint/addPoints which
    // call scratch.get(). Must be initialized before those table fields.
    private val scratch = ScratchLocal { PointScratch() }

    /** Get thread-local scratch. Call once at the top-level entry point. */
    internal fun getScratch(): PointScratch = scratch.get()

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
    private val G_TABLE_SIZE = 1 shl (WINDOW_G - 2) // 1024 for w=12

    /**
     * Precomputed G odd-multiples for wNAF: gOddTable[i] = (2i+1)·G as affine, for i in 0..G_TABLE_SIZE-1.
     * Used by mulG and mulDoubleG. Eagerly initialized to avoid SynchronizedLazyImpl
     * dispatch on every verify call (~200 instructions of lock-check overhead per access).
     */
    private val gOddTable: Array<AffinePoint> = buildGOddTable()

    /** Precomputed λ(G) odd-multiples for GLV: gLamTable[i] = λ((2i+1)·G) as affine. */
    private val gLamTable: Array<AffinePoint> =
        Array(G_TABLE_SIZE) { AffinePoint(FieldP.mul(gOddTable[it].x, Glv.BETA), gOddTable[it].y.copyOf()) }

    // ==================== P-side wNAF table cache ====================
    //
    // In mulDoubleG (verify), we build an 8-entry wNAF-5 affine table for the
    // public key P on every call: [1P, 3P, 5P, ..., 15P] plus their GLV λ
    // counterparts. This costs ~437 field ops (~27% of mulDoubleG, ~20% of verify).
    //
    // For Nostr, the same pubkeys are verified repeatedly (many events per author).
    // This cache stores the P-side affine tables keyed by the point's x-coordinate,
    // so repeated verifications for the same pubkey skip the table build entirely.
    //
    // 256 entries × 16 AffinePoints × 64 bytes = ~256KB total cache.

    // 1024 entries to cover ~1000 followed pubkeys with minimal collisions.
    // Memory: 1024 × 16 AffinePoints × 64 bytes = ~1MB. Acceptable for mobile.
    private const val P_TABLE_CACHE_SIZE = 1024
    private const val P_TABLE_CACHE_MASK = P_TABLE_CACHE_SIZE - 1

    private class CachedPTable(
        val px: LongArray, // x-coordinate of the point (cache key, 4 limbs)
        val pOdd: Array<AffinePoint>, // 8 affine odd-multiples of P
        val pLamOdd: Array<AffinePoint>, // 8 affine odd-multiples of λ(P)
    )

    private val pTableCache = arrayOfNulls<CachedPTable>(P_TABLE_CACHE_SIZE)

    /** Hash a field element to a cache slot index. */
    private fun cacheSlot(px: LongArray): Int = (px[0].toInt() xor px[1].toInt().shl(3)) and P_TABLE_CACHE_MASK

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

    private val combTable: Array<AffinePoint> = buildCombTable()

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
                val changedBit = m.countTrailingZeroBits()
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

        // Split scalar via GLV: scalar = k₁ + k₂·λ (allocation-free)
        val split = Glv.splitScalarInto(s.splitK1, s.splitK2, scalar, s.splitWide, s.splitT1, s.splitT2)
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

        // Ping-pong: alternate between two point buffers to avoid copyFrom after
        // every addition. Saves ~20 copyFroms per call (each = 12 Long copies).
        // Also avoids the internal copy in doublePoint (out===inp path).
        var cur = out
        var alt = s.mixTmp
        cur.setInfinity()
        val negY = s.mixNegY

        for (i in bits - 1 downTo 0) {
            doublePoint(alt, cur, s)
            var t = cur
            cur = alt
            alt = t
            if (addWnafMixedPP(cur, alt, negY, wnaf1, i, pOdd, split.negK1, s)) {
                t = cur
                cur = alt
                alt = t
            }
            if (addWnafMixedPP(cur, alt, negY, wnaf2, i, pLamOdd, split.negK2, s)) {
                t = cur
                cur = alt
                alt = t
            }
        }
        if (cur !== out) out.copyFrom(cur)
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

        // Ping-pong: alternate between out and s.mixTmp to avoid copyFrom after
        // every addMixed and the internal copy in in-place doublePoint.
        // Also eliminates the MutablePoint() allocation that was here before.
        var cur = out
        var alt = s.mixTmp
        cur.setInfinity()

        for (combOff in COMB_SPACING - 1 downTo 0) {
            if (combOff < COMB_SPACING - 1) {
                doublePoint(alt, cur, s)
                val t = cur
                cur = alt
                alt = t
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
                    addMixed(alt, cur, entry.x, entry.y, s)
                    val t = cur
                    cur = alt
                    alt = t
                }
            }
        }
        if (cur !== out) out.copyFrom(cur)
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

        // Split scalars via GLV decomposition (allocation-free).
        // sSplit writes into splitK1/K2, then wNAF encodes them immediately
        // before eSplit overwrites the same scratch buffers.
        val sSplit = Glv.splitScalarInto(sc.splitK1, sc.splitK2, s, sc.splitWide, sc.splitT1, sc.splitT2)
        Glv.wnafInto(sc.wnaf1, sc.wnafTmp, sSplit.k1, WINDOW_G, 129)
        Glv.wnafInto(sc.wnaf2, sc.wnafTmp, sSplit.k2, WINDOW_G, 129)
        // Now safe to reuse splitK1/K2 for the e scalar
        val eSplit = Glv.splitScalarInto(sc.splitK1, sc.splitK2, e, sc.splitWide, sc.splitT1, sc.splitT2)
        Glv.wnafInto(sc.wnaf3, sc.wnafTmp, eSplit.k1, wP, 129)
        Glv.wnafInto(sc.wnaf4, sc.wnafTmp, eSplit.k2, wP, 129)
        val wnafS1 = sc.wnaf1
        val wnafS2 = sc.wnaf2
        val wnafE1 = sc.wnaf3
        val wnafE2 = sc.wnaf4

        // G tables: precomputed and cached (no per-verify allocation)
        val gOdd = gOddTable
        val gLam = gLamTable

        // P-side tables: check cache first, build only on miss.
        // On cache hit, copies 16 affine points from cache (~trivial vs ~437 field ops to build).
        val pOdd: Array<AffinePoint>
        val pLamOdd: Array<AffinePoint>
        val cacheSlot = cacheSlot(p.x)
        val cached = pTableCache[cacheSlot]
        if (cached != null && U256.cmp(cached.px, p.x) == 0) {
            // Cache hit — use cached affine tables directly (no copy needed)
            pOdd = cached.pOdd
            pLamOdd = cached.pLamOdd
        } else {
            // Cache miss — build tables and store in cache
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
            // Batch-convert to affine (into scratch arrays)
            batchToAffinePair(pOddJac, pLamOddJac, sc.pOddAff, sc.pLamOddAff, sc)

            // Store in cache (allocate new arrays so they're independent of scratch)
            val cachedPOdd =
                Array(pTableSize) {
                    AffinePoint(sc.pOddAff[it].x.copyOf(), sc.pOddAff[it].y.copyOf())
                }
            val cachedPLamOdd =
                Array(pTableSize) {
                    AffinePoint(sc.pLamOddAff[it].x.copyOf(), sc.pLamOddAff[it].y.copyOf())
                }
            pTableCache[cacheSlot] = CachedPTable(p.x.copyOf(), cachedPOdd, cachedPLamOdd)

            pOdd = cachedPOdd
            pLamOdd = cachedPLamOdd
        }

        // Find highest non-zero digit across all 4 streams
        var bits = 129 + WINDOW_G // max possible wNAF length
        while (bits > 0 && wnafS1[bits - 1] == 0 && wnafS2[bits - 1] == 0 &&
            wnafE1[bits - 1] == 0 && wnafE2[bits - 1] == 0
        ) {
            bits--
        }

        // Ping-pong: alternate between out and sc.mixTmp to avoid copyFrom after
        // every addition (~170 copies per verify → at most 1). Also avoids the
        // internal copy buffer in doublePoint's out===inp path (~130 per verify).
        var cur = out
        var alt = sc.mixTmp
        cur.setInfinity()
        val negY = sc.mixNegY

        // Inner loop: double + conditionally add from 4 wNAF streams.
        //
        // The wNAF zero-check is INLINED here rather than delegated to
        // addWnafMixedPP. ~70% of digits are zero, so this avoids ~364
        // function calls per verify. Each function call on ART has ~5-8ns
        // overhead (parameter null checks, frame setup), saving ~2-3μs total.
        val negK1s = sSplit.negK1
        val negK2s = sSplit.negK2
        val negK1e = eSplit.negK1
        val negK2e = eSplit.negK2

        for (i in bits - 1 downTo 0) {
            doublePoint(alt, cur, sc)
            var t = cur
            cur = alt
            alt = t

            var d: Int

            // Stream 1: s₁ (G-side)
            d = wnafS1[i]
            if (d != 0) {
                val idx = (if (d > 0) d else -d) / 2
                if ((d < 0) xor negK1s) {
                    FieldP.neg(negY, gOdd[idx].y)
                    addMixed(alt, cur, gOdd[idx].x, negY, sc)
                } else {
                    addMixed(alt, cur, gOdd[idx].x, gOdd[idx].y, sc)
                }
                t = cur
                cur = alt
                alt = t
            }

            // Stream 2: s₂ (λ(G)-side)
            d = wnafS2[i]
            if (d != 0) {
                val idx = (if (d > 0) d else -d) / 2
                if ((d < 0) xor negK2s) {
                    FieldP.neg(negY, gLam[idx].y)
                    addMixed(alt, cur, gLam[idx].x, negY, sc)
                } else {
                    addMixed(alt, cur, gLam[idx].x, gLam[idx].y, sc)
                }
                t = cur
                cur = alt
                alt = t
            }

            // Stream 3: e₁ (P-side)
            d = wnafE1[i]
            if (d != 0) {
                val idx = (if (d > 0) d else -d) / 2
                if ((d < 0) xor negK1e) {
                    FieldP.neg(negY, pOdd[idx].y)
                    addMixed(alt, cur, pOdd[idx].x, negY, sc)
                } else {
                    addMixed(alt, cur, pOdd[idx].x, pOdd[idx].y, sc)
                }
                t = cur
                cur = alt
                alt = t
            }

            // Stream 4: e₂ (λ(P)-side)
            d = wnafE2[i]
            if (d != 0) {
                val idx = (if (d > 0) d else -d) / 2
                if ((d < 0) xor negK2e) {
                    FieldP.neg(negY, pLamOdd[idx].y)
                    addMixed(alt, cur, pLamOdd[idx].x, negY, sc)
                } else {
                    addMixed(alt, cur, pLamOdd[idx].x, pLamOdd[idx].y, sc)
                }
                t = cur
                cur = alt
                alt = t
            }
        }
        if (cur !== out) out.copyFrom(cur)
    }

    /**
     * Process one wNAF digit with mixed addition (ping-pong version).
     * Reads from `cur`, writes result to `alt`. Returns true if an addition was
     * performed (caller should swap cur/alt references).
     *
     * This avoids the copyFrom after every addition — the caller swaps references
     * instead (free: just local variable reassignment).
     */
    private fun addWnafMixedPP(
        cur: MutablePoint,
        alt: MutablePoint,
        negY: LongArray,
        wnafDigits: IntArray,
        bitIndex: Int,
        table: Array<AffinePoint>,
        glvNeg: Boolean,
        s: PointScratch,
    ): Boolean {
        if (bitIndex >= wnafDigits.size) return false
        val d = wnafDigits[bitIndex]
        if (d == 0) return false
        val idx = (if (d > 0) d else -d) / 2
        val effectiveNeg = (d < 0) xor glvNeg
        if (!effectiveNeg) {
            addMixed(alt, cur, table[idx].x, table[idx].y, s)
        } else {
            FieldP.neg(negY, table[idx].y)
            addMixed(alt, cur, table[idx].x, negY, s)
        }
        return true
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

    /** Convert Jacobian → affine (convenience, allocates temps). For one-time init paths. */
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

    /** Convert Jacobian → affine using pre-allocated scratch (hot path). */
    fun toAffine(
        p: MutablePoint,
        outX: LongArray,
        outY: LongArray,
        s: PointScratch,
    ): Boolean {
        if (p.isInfinity()) return false
        FieldP.inv(s.zInv, p.z)
        FieldP.sqr(s.zInv2, s.zInv)
        FieldP.mul(s.zInv3, s.zInv2, s.zInv)
        FieldP.mul(outX, p.x, s.zInv2)
        FieldP.mul(outY, p.y, s.zInv3)
        return true
    }

    /** Convert Jacobian → affine x-only using pre-allocated scratch (hot path). */
    fun toAffineX(
        p: MutablePoint,
        outX: LongArray,
        s: PointScratch,
    ): Boolean {
        if (p.isInfinity()) return false
        FieldP.inv(s.zInv, p.z)
        FieldP.sqr(s.zInv2, s.zInv)
        FieldP.mul(outX, p.x, s.zInv2)
        return true
    }
}
