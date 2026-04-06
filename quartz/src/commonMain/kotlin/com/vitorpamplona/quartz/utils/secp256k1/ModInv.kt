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
// SAFEGCD-BASED MODULAR INVERSE FOR secp256k1 FIELD ELEMENTS
// =====================================================================================
//
// Implements the Bernstein-Yang (2019) "divsteps" algorithm for computing modular
// inverses. This is significantly faster than the Fermat approach (a^(p-2) mod p)
// which requires 255 squarings + 15 multiplications.
//
// The algorithm maintains (f, g, d, e) with invariants:
//   d * input ≡ f (mod p)
//   e * input ≡ g (mod p)
// and iteratively reduces g toward 0 via divstep transitions. After ~741 steps,
// g = 0 and f = ±1, so d = ±input^{-1}.
//
// Steps are batched in groups of 62, processing the bottom 62 bits of f, g to
// produce a 2×2 transition matrix, then applying it to the full-precision values.
// This requires 12 rounds × 62 steps = 744 ≥ 741 needed for 256-bit modulus.
//
// Based on bitcoin-core/secp256k1's modinv64 implementation.
// =====================================================================================

internal object ModInv {
    private const val M62 = (1L shl 62) - 1 // 0x3FFFFFFFFFFFFFFF

    // p^{-1} mod 2^62, computed via Hensel lifting at class init.
    // Used to compute the correction factor in updateDE.
    private val P_INV_62: Long =
        run {
            val pLo = FieldP.P[0] // low 64 bits of p
            var x = 1L // p is odd so p*1 ≡ 1 (mod 2)
            // Each iteration doubles the number of correct bits
            x *= 2 - pLo * x // mod 2^2
            x *= 2 - pLo * x // mod 2^4
            x *= 2 - pLo * x // mod 2^8
            x *= 2 - pLo * x // mod 2^16
            x *= 2 - pLo * x // mod 2^32
            x *= 2 - pLo * x // mod 2^64
            x and M62
        }

    // p as 5×62-bit signed limbs (little-endian, each limb in [0, 2^62))
    private val P62: LongArray = toLimbs62(FieldP.P)

    /**
     * Compute modular inverse: out = a^{-1} mod p using safegcd.
     */
    fun modinv(
        out: LongArray,
        a: LongArray,
    ) {
        // f = p, g = a (as 5×62-bit signed limbs)
        val f = toLimbs62(FieldP.P)
        val g = toLimbs62(a)
        // d = 0, e = 1 (as 5×62-bit signed limbs, values mod p)
        val d = LongArray(5)
        val e = longArrayOf(1, 0, 0, 0, 0)

        var delta = 1L

        // 12 rounds of 62 divsteps = 744 total (need ≥ 741 for 256-bit modulus)
        for (round in 0 until 12) {
            // Bottom 64 bits of f and g for the inner loop
            val fBot = f[0] or (f[1] shl 62)
            val gBot = g[0] or (g[1] shl 62)

            // Run 62 divsteps on the truncated values, get transition matrix
            val t = divsteps62var(delta, fBot, gBot)
            delta = t.delta

            // Apply matrix to full-precision (f, g) and (d, e)
            updateFG(f, g, t)
            updateDE(d, e, t)
        }

        // At this point g ≈ 0, f = ±1.
        // d * a ≡ f (mod p), so if f = 1 then d = a^{-1}
        // if f = -1, negate d
        normalize5(d)
        if (isNegativeOne(f)) {
            negateMod(d)
        }
        fromLimbs62(out, d)
        FieldP.reduceSelf(out)
    }

    // ==================== Transition Matrix ====================

    private class Trans(
        val delta: Long,
        val u: Long,
        val v: Long,
        val q: Long,
        val r: Long,
    )

    /**
     * Variable-time 62 divsteps on bottom 64 bits of f, g.
     * Returns the transition matrix [u v; q r] and updated delta.
     *
     * Invariant maintained:
     *   u * f_orig + v * g_orig = f_current * 2^steps_done
     *   q * f_orig + r * g_orig = g_current * 2^steps_done
     */
    private fun divsteps62var(
        delta: Long,
        f0: Long,
        g0: Long,
    ): Trans {
        var d = delta
        var f = f0
        var g = g0
        var u = 1L
        var v = 0L
        var q = 0L
        var r = 1L
        var steps = 62

        while (steps > 0) {
            if (g == 0L) {
                u = u shl steps
                v = v shl steps
                d += steps
                break
            }

            // Count and skip trailing zeros in g
            val zeros = g.countTrailingZeroBits().coerceAtMost(steps)
            if (zeros > 0) {
                g = g shr zeros // arithmetic shift
                u = u shl zeros
                v = v shl zeros
                d += zeros
                steps -= zeros
                if (steps == 0) break
            }

            // g is odd. Apply divstep.
            if (d > 0) {
                // Swap: f_new = g, g_new = (g - f)/2
                val tU = u
                val tV = v
                val tF = f
                u = 2 * q
                v = 2 * r
                q = q - tU
                r = r - tV
                f = g
                g = (g - tF) shr 1
                d = 1 - d
            } else {
                // No swap: g_new = (g + f)/2
                q += u
                r += v
                u *= 2
                v *= 2
                g = (g + f) shr 1
                d += 1
            }
            steps--
        }

        return Trans(d, u, v, q, r)
    }

    // ==================== Matrix Application ====================

    /**
     * Apply transition matrix to (f, g): [f,g] = [u,v; q,r] * [f,g] / 2^62
     * Uses 128-bit signed arithmetic via multiplyHigh.
     */
    private fun updateFG(
        f: LongArray,
        g: LongArray,
        t: Trans,
    ) {
        // Save originals (both newF and newG depend on original f, g)
        val of = f.copyOf()
        val og = g.copyOf()

        // f_new = (u*of + v*og) / 2^62
        updateRow(f, of, og, t.u, t.v)

        // g_new = (q*of + r*og) / 2^62
        updateRow(g, of, og, t.q, t.r)
    }

    // Computes out = (s1*a + s2*b) / 2^62 using 128-bit accumulation
    private fun updateRow(
        out: LongArray,
        a: LongArray,
        b: LongArray,
        s1: Long,
        s2: Long,
    ) {
        // Accumulate limb-by-limb with 128-bit carry
        var cLo = 0L
        var cHi = 0L

        for (i in 0 until 5) {
            // acc = s1*a[i] + s2*b[i] + carry (128-bit signed)
            var sLo = s1 * a[i]
            var sHi = multiplyHigh(s1, a[i])
            val bLo = s2 * b[i]
            val bHi = multiplyHigh(s2, b[i])

            // sLo:sHi += bLo:bHi
            val prevSLo = sLo
            sLo += bLo
            sHi += bHi
            if (sLo.toULong() < prevSLo.toULong()) sHi++

            // += carry
            val prevSLo2 = sLo
            sLo += cLo
            if (sLo.toULong() < prevSLo2.toULong()) sHi++
            sHi += cHi

            if (i == 0) {
                // Low 62 bits should be zero. Just shift right by 62.
                cLo = (sLo ushr 62) or (sHi shl 2)
                cHi = sHi shr 62 // arithmetic
            } else {
                out[i - 1] = sLo and M62
                cLo = (sLo ushr 62) or (sHi shl 2)
                cHi = sHi shr 62
            }
        }
        out[4] = cLo
    }

    /**
     * Apply transition matrix to (d, e) modulo p: [d,e] = [u,v; q,r] * [d,e] / 2^62 mod p.
     *
     * The division by 2^62 is exact after adding a suitable multiple of p to make
     * the low 62 bits zero. This uses the precomputed P_INV_62 = p^{-1} mod 2^62.
     */
    private fun updateDE(
        d: LongArray,
        e: LongArray,
        t: Trans,
    ) {
        val od = d.copyOf()
        val oe = e.copyOf()

        updateRowDE(d, od, oe, t.u, t.v)
        updateRowDE(e, od, oe, t.q, t.r)
    }

    private fun updateRowDE(
        out: LongArray,
        a: LongArray,
        b: LongArray,
        s1: Long,
        s2: Long,
    ) {
        // Step 1: compute the low 62 bits of s1*a + s2*b
        val mdLo = (s1 * a[0] + s2 * b[0]) and M62

        // Step 2: compute correction factor so that (s1*a + s2*b + cd*p) ≡ 0 (mod 2^62)
        val cd = (-mdLo * P_INV_62) and M62

        // Step 3: accumulate s1*a[i] + s2*b[i] + cd*p[i], then shift right by 62
        var cLo = 0L
        var cHi = 0L

        for (i in 0 until 5) {
            // acc = s1*a[i] + s2*b[i] + cd*P62[i] + carry
            var sLo = s1 * a[i]
            var sHi = multiplyHigh(s1, a[i])

            // += s2*b[i]
            var tLo = s2 * b[i]
            var tHi = multiplyHigh(s2, b[i])
            var prev = sLo
            sLo += tLo
            sHi += tHi
            if (sLo.toULong() < prev.toULong()) sHi++

            // += cd*P62[i]
            tLo = cd * P62[i]
            tHi = multiplyHigh(cd, P62[i])
            prev = sLo
            sLo += tLo
            sHi += tHi
            if (sLo.toULong() < prev.toULong()) sHi++

            // += carry
            prev = sLo
            sLo += cLo
            if (sLo.toULong() < prev.toULong()) sHi++
            sHi += cHi

            if (i == 0) {
                // Low 62 bits are now zero by construction. Shift right.
                cLo = (sLo ushr 62) or (sHi shl 2)
                cHi = sHi shr 62
            } else {
                out[i - 1] = sLo and M62
                cLo = (sLo ushr 62) or (sHi shl 2)
                cHi = sHi shr 62
            }
        }
        out[4] = cLo
    }

    // ==================== Limb Conversion ====================

    // Convert 4×64-bit unsigned to 5×62-bit unsigned limbs
    private fun toLimbs62(a: LongArray): LongArray {
        val r = LongArray(5)
        r[0] = a[0] and M62
        r[1] = ((a[0] ushr 62) or (a[1] shl 2)) and M62
        r[2] = ((a[1] ushr 60) or (a[2] shl 4)) and M62
        r[3] = ((a[2] ushr 58) or (a[3] shl 6)) and M62
        r[4] = a[3] ushr 56
        return r
    }

    // Convert 5×62-bit signed limbs to 4×64-bit unsigned
    // Assumes value is in [0, p) after normalization.
    private fun fromLimbs62(
        out: LongArray,
        a: LongArray,
    ) {
        out[0] = (a[0] and M62) or (a[1] shl 62)
        out[1] = (a[1] ushr 2) or (a[2] shl 60)
        out[2] = (a[2] ushr 4) or (a[3] shl 58)
        out[3] = (a[3] ushr 6) or (a[4] shl 56)
    }

    // Normalize 5×62-bit signed limbs: propagate carries so all limbs are in [0, 2^62)
    private fun normalize5(a: LongArray) {
        for (i in 0 until 4) {
            val carry = a[i] shr 62 // arithmetic shift (preserves sign)
            a[i] = a[i] and M62
            a[i + 1] += carry
        }
        // If a[4] is negative, the entire number is negative → add p
        if (a[4] < 0) {
            addP62(a)
            // Normalize again
            for (i in 0 until 4) {
                val carry = a[i] shr 62
                a[i] = a[i] and M62
                a[i + 1] += carry
            }
        }
    }

    // Check if 5×62-bit limb number equals -1 (f should be ±1 at the end)
    private fun isNegativeOne(f: LongArray): Boolean {
        // -1 in 5×62-bit signed: all limbs = M62 (i.e., 2^62-1) except possibly the top
        // More robust: normalize and check if limb[0..3] are M62 and limb[4] = M62
        // Or just check the sign of the number
        // After normalization, f should be exactly 1 or p-1 (which is ≡ -1 mod p)
        // A simpler check: if the value is p-1, it means f was -1
        // For now, just check if f is negative before normalization:
        // Sum the value: the sign is determined by the highest non-zero limb
        val norm = f.copyOf()
        for (i in 0 until 4) {
            val carry = norm[i] shr 62
            norm[i] = norm[i] and M62
            norm[i + 1] += carry
        }
        return norm[4] < 0 || (norm[4] == 0L && norm[3] == 0L && norm[2] == 0L && norm[1] == 0L && norm[0] < 0)
    }

    // Negate d modulo p: d = p - d
    private fun negateMod(d: LongArray) {
        var borrow = 0L
        for (i in 0 until 5) {
            val diff = P62[i] - d[i] - borrow
            d[i] = diff and M62
            borrow = -(diff shr 62) // 0 or 1
        }
    }

    // Add p (in 62-bit limbs) to a
    private fun addP62(a: LongArray) {
        var carry = 0L
        for (i in 0 until 5) {
            val sum = a[i] + P62[i] + carry
            a[i] = sum and M62
            carry = sum shr 62
        }
    }
}
