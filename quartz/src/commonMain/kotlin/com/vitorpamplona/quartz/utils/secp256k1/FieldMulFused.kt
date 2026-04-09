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
// FUSED 128-BIT MULTIPLY: computes both lo and hi from shared sub-products.
//
// The current fieldMulReduceWith uses:
//   lo = a * b           (1 hardware mul — CPU computes all sub-products internally)
//   hi = umulh(a, b)     (4 imul fallback — recomputes the same 4 sub-products)
//
// This fused version computes both from 4 shared sub-products (aLo*bLo, aLo*bHi,
// aHi*bLo, aHi*bHi), saving 1 multiply per wide product.
//
// With ~20 wide multiplies per field mul, that's ~20 fewer multiplies per field op.
// Across ~750 field ops per verify: ~15,000 fewer multiply instructions.
//
// The inline lambda consumer pattern returns two values without heap allocation:
//   mulFull(a, b) { lo, hi -> ... }  // both inlined at call site
//
// USE ON: platforms where umulh is a software fallback (Android <35, K/Native).
// DO NOT USE ON: JVM/HotSpot where Math.unsignedMultiplyHigh is a hardware intrinsic.
// =====================================================================================

private const val MASK32 = 0xFFFFFFFFL

/**
 * Compute full 64×64 → 128-bit unsigned multiply from 4 sub-products.
 * Passes both lo and hi to [consume] which is inlined at the call site.
 */
@Suppress("NOTHING_TO_INLINE")
private inline fun mulFull(
    a: Long,
    b: Long,
    consume: (lo: Long, hi: Long) -> Unit,
) {
    val aLo = a and MASK32
    val aHi = a ushr 32
    val bLo = b and MASK32
    val bHi = b ushr 32
    val ll = aLo * bLo
    val lh = aLo * bHi
    val hl = aHi * bLo
    val hh = aHi * bHi
    val midSum = (ll ushr 32) + (hl and MASK32) + (lh and MASK32)
    consume(
        (ll and MASK32) or (midSum shl 32),
        hh + (hl ushr 32) + (lh ushr 32) + (midSum ushr 32),
    )
}

/** Unsigned multiply high only, from 4 sub-products. Used in reduction stage. */
@Suppress("NOTHING_TO_INLINE")
private inline fun umulhFused(
    a: Long,
    b: Long,
): Long {
    val aLo = a and MASK32
    val aHi = a ushr 32
    val bLo = b and MASK32
    val bHi = b ushr 32
    val mid1 = aHi * bLo
    val mid2 = aLo * bHi
    val low = aLo * bLo
    val carry = ((low ushr 32) + (mid1 and MASK32) + (mid2 and MASK32)) ushr 32
    return (aHi * bHi) + (mid1 ushr 32) + (mid2 ushr 32) + carry
}

/**
 * Fused field multiply: out = (a × b) mod p.
 * Uses shared sub-products for each 128-bit multiply (4 imul instead of 5).
 */
@Suppress("LongMethod")
internal fun fieldMulReduceFused(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    val a0 = a[0]
    val a1 = a[1]
    val a2 = a[2]
    val a3 = a[3]
    val b0 = b[0]
    val b1 = b[1]
    val b2 = b[2]
    val b3 = b[3]
    var s: Long = 0L
    var c1: Long
    var c2: Long
    var carry: Long = 0L

    // Row 0: a0 × [b0,b1,b2,b3]
    mulFull(a0, b0) { lo, hi ->
        w[0] = lo
        carry = hi
    }

    mulFull(a0, b1) { lo, hi ->
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w[1] = s
        carry = hi + c1
    }
    mulFull(a0, b2) { lo, hi ->
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w[2] = s
        carry = hi + c1
    }
    mulFull(a0, b3) { lo, hi ->
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w[3] = s
        w[4] = hi + c1
    }

    // Row 1: a1 × [b0,b1,b2,b3]
    mulFull(a1, b0) { lo, hi ->
        val prev = w[1]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w[1] = s
        carry = hi + c1
    }
    mulFull(a1, b1) { lo, hi ->
        val prev = w[2]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[2] = s
        carry = hi + c1 + c2
    }
    mulFull(a1, b2) { lo, hi ->
        val prev = w[3]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[3] = s
        carry = hi + c1 + c2
    }
    mulFull(a1, b3) { lo, hi ->
        val prev = w[4]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[4] = s
        w[5] = hi + c1 + c2
    }

    // Row 2: a2 × [b0,b1,b2,b3]
    mulFull(a2, b0) { lo, hi ->
        val prev = w[2]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w[2] = s
        carry = hi + c1
    }
    mulFull(a2, b1) { lo, hi ->
        val prev = w[3]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[3] = s
        carry = hi + c1 + c2
    }
    mulFull(a2, b2) { lo, hi ->
        val prev = w[4]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[4] = s
        carry = hi + c1 + c2
    }
    mulFull(a2, b3) { lo, hi ->
        val prev = w[5]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[5] = s
        w[6] = hi + c1 + c2
    }

    // Row 3: a3 × [b0,b1,b2,b3]
    mulFull(a3, b0) { lo, hi ->
        val prev = w[3]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w[3] = s
        carry = hi + c1
    }
    mulFull(a3, b1) { lo, hi ->
        val prev = w[4]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[4] = s
        carry = hi + c1 + c2
    }
    mulFull(a3, b2) { lo, hi ->
        val prev = w[5]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[5] = s
        carry = hi + c1 + c2
    }
    mulFull(a3, b3) { lo, hi ->
        val prev = w[6]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[6] = s
        w[7] = hi + c1 + c2
    }

    // Reduction uses umulh only (lo computed with hardware *)
    reduceWideInline(out, w) { x, y -> umulhFused(x, y) }
}

/**
 * Fused field squaring: out = a² mod p.
 * Uses shared sub-products for each 128-bit multiply.
 */
@Suppress("LongMethod")
internal fun fieldSqrReduceFused(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    val a0 = a[0]
    val a1 = a[1]
    val a2 = a[2]
    val a3 = a[3]
    var s: Long = 0L
    var c1: Long
    var c2: Long
    var carry: Long = 0L

    // Pass 1: cross-products a[i]*a[j] for i < j
    w[0] = 0L
    mulFull(a0, a1) { lo, hi ->
        w[1] = lo
        carry = hi
    }

    mulFull(a0, a2) { lo, hi ->
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w[2] = s
        carry = hi + c1
    }
    mulFull(a0, a3) { lo, hi ->
        s = lo + carry
        c1 = if (uLtInline(s, lo)) 1L else 0L
        w[3] = s
        w[4] = hi + c1
    }

    mulFull(a1, a2) { lo, hi ->
        val prev = w[3]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w[3] = s
        carry = hi + c1
    }
    mulFull(a1, a3) { lo, hi ->
        val prev = w[4]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        s += carry
        c2 = if (uLtInline(s, carry)) 1L else 0L
        w[4] = s
        w[5] = hi + c1 + c2
    }

    mulFull(a2, a3) { lo, hi ->
        val prev = w[5]
        s = prev + lo
        c1 = if (uLtInline(s, prev)) 1L else 0L
        w[5] = s
        w[6] = hi + c1
    }

    // Pass 2: double all cross-products (shift left by 1 bit)
    var v = w[1]
    w[1] = v shl 1
    var shiftCarry = v ushr 63
    v = w[2]
    w[2] = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w[3]
    w[3] = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w[4]
    w[4] = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w[5]
    w[5] = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w[6]
    w[6] = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    w[7] = shiftCarry

    // Pass 3: add diagonal products a[i]²
    // Use the original pattern: lo = a*a, hi = umulh(a,a) since the diagonal
    // products need careful carry threading that's hard to do inside lambdas.
    var dLo: Long
    var dHi: Long
    var prev: Long

    dLo = a0 * a0
    dHi = umulhFused(a0, a0)
    w[0] = dLo
    s = w[1] + dHi
    c1 = if (uLtInline(s, w[1])) 1L else 0L
    w[1] = s
    var dCarry = c1

    dLo = a1 * a1
    dHi = umulhFused(a1, a1)
    s = w[2] + dLo
    c1 = if (uLtInline(s, w[2])) 1L else 0L
    s += dCarry
    c2 = if (uLtInline(s, dCarry)) 1L else 0L
    w[2] = s
    prev = w[3] + dHi
    val c3a = if (uLtInline(prev, w[3])) 1L else 0L
    prev += c1 + c2
    val c4a = if (uLtInline(prev, c1 + c2)) 1L else 0L
    w[3] = prev
    dCarry = c3a + c4a

    dLo = a2 * a2
    dHi = umulhFused(a2, a2)
    s = w[4] + dLo
    c1 = if (uLtInline(s, w[4])) 1L else 0L
    s += dCarry
    c2 = if (uLtInline(s, dCarry)) 1L else 0L
    w[4] = s
    prev = w[5] + dHi
    val c3b = if (uLtInline(prev, w[5])) 1L else 0L
    prev += c1 + c2
    val c4b = if (uLtInline(prev, c1 + c2)) 1L else 0L
    w[5] = prev
    dCarry = c3b + c4b

    dLo = a3 * a3
    dHi = umulhFused(a3, a3)
    s = w[6] + dLo
    c1 = if (uLtInline(s, w[6])) 1L else 0L
    s += dCarry
    c2 = if (uLtInline(s, dCarry)) 1L else 0L
    w[6] = s
    prev = w[7] + dHi
    prev += c1 + c2
    w[7] = prev

    // Reduction
    reduceWideInline(out, w) { x, y -> umulhFused(x, y) }
}
