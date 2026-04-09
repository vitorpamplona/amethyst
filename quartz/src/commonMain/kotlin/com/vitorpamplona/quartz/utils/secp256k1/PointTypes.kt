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

import kotlin.jvm.JvmField

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
 *
 * @JvmField is REQUIRED on x/y/z. Without it, Kotlin generates getX()/getY()/getZ()
 * virtual getter methods. Bytecode analysis showed ~7,450 invokevirtual getter calls
 * per Schnorr verify (130 doublePoints × 13 getXYZ each + 160 addMixed × 23 each).
 * @JvmField compiles property access to direct field reads (getfield bytecode), which
 * is ~3-4ns faster per access on ART. On non-JVM targets, @JvmField is ignored.
 */
internal class MutablePoint(
    @JvmField val x: LongArray = LongArray(4),
    @JvmField val y: LongArray = LongArray(4),
    @JvmField val z: LongArray = LongArray(4),
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
 * @JvmField: see MutablePoint for rationale (eliminates virtual getter calls).
 */
internal class AffinePoint(
    @JvmField val x: LongArray = LongArray(4),
    @JvmField val y: LongArray = LongArray(4),
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
 *
 * @JvmField on ALL properties: without it, each property access compiles to an
 * invokevirtual getter call. Bytecode analysis showed ~2,000+ getter calls per
 * verify from ECPoint accessing scratch.t, scratch.w, scratch.dblCopy, etc.
 * @JvmField compiles these to direct field reads. On non-JVM targets, ignored.
 */
internal class PointScratch {
    @JvmField val t = Array(12) { LongArray(4) }

    @JvmField val dblCopy = MutablePoint()

    @JvmField val w = LongArray(8)

    @JvmField val wnaf1 = IntArray(145)

    @JvmField val wnaf2 = IntArray(145)

    @JvmField val wnaf3 = IntArray(145)

    @JvmField val wnaf4 = IntArray(145)

    @JvmField val wnafTmp = LongArray(4)

    @JvmField val mixTmp = MutablePoint()

    @JvmField val mixNegY = LongArray(4)

    @JvmField val pOddJac = Array(8) { MutablePoint() }

    @JvmField val pLamOddJac = Array(8) { MutablePoint() }

    @JvmField val pOddAff = Array(8) { AffinePoint() }

    @JvmField val pLamOddAff = Array(8) { AffinePoint() }

    @JvmField val p2 = MutablePoint()

    @JvmField val cumZ = Array(8) { LongArray(4) }

    @JvmField val batchInv = LongArray(4)

    @JvmField val batchZInv = LongArray(4)

    @JvmField val batchZInv2 = LongArray(4)

    @JvmField val batchZInv3 = LongArray(4)

    @JvmField val splitWide = LongArray(8)

    @JvmField val splitT1 = LongArray(4)

    @JvmField val splitT2 = LongArray(4)

    @JvmField val splitK1 = LongArray(4)

    @JvmField val splitK2 = LongArray(4)

    @JvmField val zInv = LongArray(4)

    @JvmField val zInv2 = LongArray(4)

    @JvmField val zInv3 = LongArray(4)

    @JvmField val entryPx = LongArray(4)

    @JvmField val entryPy = LongArray(4)

    @JvmField val entryPoint = MutablePoint()

    @JvmField val entryResult = MutablePoint()

    @JvmField val entryTmp = LongArray(4)

    @JvmField val entryTmp2 = LongArray(4)

    // Pre-allocated byte buffers for sign/verify (eliminates ByteArray allocations).
    // hashBuf: reusable buffer for BIP-340 tagged hash inputs (prefix(64) + fields).
    // The max size is 64 + 32 + 32 + msgLen. For 32-byte messages (event IDs), that's 160.
    // For larger messages, signSchnorrInternal must still allocate.
    @JvmField val hashBuf = ByteArray(256)

    // 32-byte scratch for serialized field elements / scalars (avoids U256.toBytes allocs)
    @JvmField val bytesTmp1 = ByteArray(32)

    @JvmField val bytesTmp2 = ByteArray(32)

    // Scratch LongArray(4) for intermediate scalar results (avoids ScalarN alloc)
    @JvmField val scalarTmp1 = LongArray(4)

    @JvmField val scalarTmp2 = LongArray(4)

    @JvmField val scalarTmp3 = LongArray(4)
}
