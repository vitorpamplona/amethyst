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
 *
 * Points are stored in Jacobian projective coordinates (X, Y, Z) which represent
 * the affine point (X/Z², Y/Z³). This avoids expensive field inversions during
 * intermediate steps — inversion is only needed once at the end to convert back
 * to affine (x, y) form.
 *
 * The "point at infinity" (identity element) is represented by Z = 0.
 *
 * Mutable to avoid allocating new objects during the inner loop of scalar
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

/**
 * Pre-allocated scratch space for point operations. Each thread gets its own
 * instance via [ScratchLocal] to avoid allocation and ThreadLocal lookups in the
 * inner loops of scalar multiplication.
 *
 * The 12 temp buffers (t[0]..t[11]) are shared across doublePoint and addPoints —
 * this is safe because these functions only call each other in the equal-point
 * degenerate case, which returns immediately after the recursive call without
 * using the temps further.
 *
 * The wide buffer (LongArray(8)) is pre-fetched once per top-level operation and
 * passed through to FieldP.mul/sqr, avoiding ~500+ ThreadLocal.get() calls per
 * scalar multiplication (~20-30ns each on JVM).
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

    // Pre-allocated scratch for Glv.splitScalar (avoids ~26 LongArray allocs per call)
    val splitWide = LongArray(8) // mulShift384 and ScalarN.mulTo scratch
    val splitT1 = LongArray(4) // temporary for mul results
    val splitT2 = LongArray(4) // temporary for mul results
    val splitK1 = LongArray(4) // output k1
    val splitK2 = LongArray(4) // output k2

    // Pre-allocated scratch for verifySchnorr (avoids per-call allocations)
    val verifyPx = LongArray(4)
    val verifyPy = LongArray(4)
    val verifyR = LongArray(4)
    val verifyS = LongArray(4)
    val verifyE = LongArray(4)
    val verifyRx = LongArray(4)
    val verifyRy = LongArray(4)
    val verifyPPoint = MutablePoint()
    val verifyResult = MutablePoint()
}
