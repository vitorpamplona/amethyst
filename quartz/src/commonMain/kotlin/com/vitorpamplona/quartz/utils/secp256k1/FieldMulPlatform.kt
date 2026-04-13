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
// FUSED FIELD MULTIPLY + REDUCE — PLATFORM-SPECIFIC INTRINSIC DISPATCH
// =====================================================================================
//
// PROBLEM:
//   The original code path was: FieldP.mul → U256.mulWide → unsignedMultiplyHigh → Math.xxx
//   Each field multiply called unsignedMultiplyHigh 20 times (16 in mulWide + 4 in reduceWide).
//   On Android, unsignedMultiplyHigh is a wrapper with per-call API-level branching:
//     if (API >= 35) Math.unsignedMultiplyHigh else if (API >= 31) Math.multiplyHigh+correction else fallback
//   ART's JIT (inlining depth ~3 levels vs HotSpot's 8+) could not inline this 6-level-deep
//   call chain, resulting in ~10,000 un-inlined function calls per Schnorr verify.
//
// SOLUTION:
//   Fuse mulWide + reduceWide into a single `inline` function (fieldMulReduceWith) that
//   accepts the multiply-high intrinsic as a `crossinline` lambda. The lambda is substituted
//   at each call site by the Kotlin compiler (not by the JIT), producing platform-specific
//   bytecode with the intrinsic call directly embedded — zero wrapper overhead.
//
// WHY `inline` + `crossinline` LAMBDA (not expect/actual for the whole function)?
//   Writing the full 200-line mulWide+reduceWide in each platform file would require
//   maintaining 3 identical copies (Android/JVM/Native) that differ only in the
//   multiply-high call. The inline+crossinline pattern keeps the arithmetic in one place
//   (commonMain) while the platform files provide just the intrinsic.
//
// WHY NOT a single function with all API branches?
//   Tested: inlining all 3 API-level branches into one fieldMulReduce created ~600 DEX
//   instructions. ART's register allocator produced suboptimal code with stack spills,
//   causing verify to REGRESS. The split approach (separate private functions per API level,
//   see FieldMulPlatform.android.kt) keeps each at ~200 DEX instructions.
//
// WHY NOT 5×52-bit limbs with lazy reduction (like C libsecp256k1)?
//   Tested: 5×52 requires 25 multiplyHigh calls per field multiply (vs our 16 with 4×64).
//   On ART where each multiplyHigh has overhead, the 56% increase in multiply calls
//   overwhelmed the savings from skipping reduceSelf after add/sub. Net result: slower.
//
// MEASURED IMPACT (Pixel 8, Android 16):
//   signSchnorr:  186,610 → 109,711 ns (-41%)
//   verifySchnorr: 160,869 → 135,885 ns (-16%)
//   Combined with uLtInline() and @JvmField: verify → 116,450 ns (-28% total)
//
// =====================================================================================

/**
 * Fused field multiplication: out = (a × b) mod p.
 * Platform implementations call fieldMulReduceWith with the best available intrinsic.
 */
internal expect fun fieldMulReduce(
    out: Fe4,
    a: Fe4,
    b: Fe4,
    w: Wide8,
)

/**
 * Fused field squaring: out = a² mod p.
 * Platform implementations call fieldSqrReduceWith with the best available intrinsic.
 */
internal expect fun fieldSqrReduce(
    out: Fe4,
    a: Fe4,
    w: Wide8,
)

// P[0] constant for reduceSelf (duplicated here because inline functions can't
// access private members of other objects).
private const val FIELD_P0 = -4294968273L // 0xFFFFFFFEFFFFFC2F

// uLtInline is defined in U256.kt (package-wide, internal inline).
// It replaces all uLt() calls in hot-path code to avoid function dispatch.

/**
 * Fused 4×4 schoolbook multiplication + mod-p reduction.
 *
 * Combines U256.mulWide and FieldP.reduceWide into a single inline function.
 * The umulh parameter is inlined at each call site, producing platform-specific
 * code with direct intrinsic calls and zero wrapper overhead.
 *
 * @param umulh Returns the upper 64 bits of the unsigned 128-bit product.
 */
@Suppress("LongMethod")
internal inline fun fieldMulReduceWith(
    out: Fe4,
    a: Fe4,
    b: Fe4,
    w: Wide8,
    umulh: (Long, Long) -> Long,
) {
    // === Stage 1: 4×4 schoolbook multiplication (16 products) ===
    val a0 = a.l0
    val a1 = a.l1
    val a2 = a.l2
    val a3 = a.l3
    val b0 = b.l0
    val b1 = b.l1
    val b2 = b.l2
    val b3 = b.l3
    var lo: Long
    var hi: Long
    var prev: Long
    var s: Long
    var c1: Long
    var c2: Long
    var carry: Long

    // Row 0: a0 × [b0,b1,b2,b3]
    lo = a0 * b0
    w.l0 = lo
    carry = umulh(a0, b0)

    lo = a0 * b1
    s = lo + carry
    c1 = if (uLtInline(s, lo)) 1L else 0L
    w.l1 = s
    carry = umulh(a0, b1) + c1

    lo = a0 * b2
    s = lo + carry
    c1 = if (uLtInline(s, lo)) 1L else 0L
    w.l2 = s
    carry = umulh(a0, b2) + c1

    lo = a0 * b3
    s = lo + carry
    c1 = if (uLtInline(s, lo)) 1L else 0L
    w.l3 = s
    w.l4 = umulh(a0, b3) + c1

    // Row 1: a1 × [b0,b1,b2,b3]
    lo = a1 * b0
    hi = umulh(a1, b0)
    prev = w.l1
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    w.l1 = s
    carry = hi + c1

    lo = a1 * b1
    hi = umulh(a1, b1)
    prev = w.l2
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l2 = s
    carry = hi + c1 + c2

    lo = a1 * b2
    hi = umulh(a1, b2)
    prev = w.l3
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l3 = s
    carry = hi + c1 + c2

    lo = a1 * b3
    hi = umulh(a1, b3)
    prev = w.l4
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l4 = s
    w.l5 = hi + c1 + c2

    // Row 2: a2 × [b0,b1,b2,b3]
    lo = a2 * b0
    hi = umulh(a2, b0)
    prev = w.l2
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    w.l2 = s
    carry = hi + c1

    lo = a2 * b1
    hi = umulh(a2, b1)
    prev = w.l3
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l3 = s
    carry = hi + c1 + c2

    lo = a2 * b2
    hi = umulh(a2, b2)
    prev = w.l4
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l4 = s
    carry = hi + c1 + c2

    lo = a2 * b3
    hi = umulh(a2, b3)
    prev = w.l5
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l5 = s
    w.l6 = hi + c1 + c2

    // Row 3: a3 × [b0,b1,b2,b3]
    lo = a3 * b0
    hi = umulh(a3, b0)
    prev = w.l3
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    w.l3 = s
    carry = hi + c1

    lo = a3 * b1
    hi = umulh(a3, b1)
    prev = w.l4
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l4 = s
    carry = hi + c1 + c2

    lo = a3 * b2
    hi = umulh(a3, b2)
    prev = w.l5
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l5 = s
    carry = hi + c1 + c2

    lo = a3 * b3
    hi = umulh(a3, b3)
    prev = w.l6
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l6 = s
    w.l7 = hi + c1 + c2

    // === Stage 2: mod-p reduction (2^256 ≡ 2^32 + 977 mod p) ===
    reduceWideInline(out, w, umulh)
}

/**
 * Fused squaring + mod-p reduction (10 products via symmetry).
 *
 * @param umulh Returns the upper 64 bits of the unsigned 128-bit product.
 */
@Suppress("LongMethod")
internal inline fun fieldSqrReduceWith(
    out: Fe4,
    a: Fe4,
    w: Wide8,
    umulh: (Long, Long) -> Long,
) {
    val a0 = a.l0
    val a1 = a.l1
    val a2 = a.l2
    val a3 = a.l3
    var lo: Long
    var hi: Long
    var prev: Long
    var s: Long
    var c1: Long
    var c2: Long
    var carry: Long
    var v: Long

    // Pass 1: cross-products a[i]*a[j] for i < j
    w.l0 = 0L
    lo = a0 * a1
    w.l1 = lo
    carry = umulh(a0, a1)

    lo = a0 * a2
    s = lo + carry
    c1 = if (uLtInline(s, lo)) 1L else 0L
    w.l2 = s
    carry = umulh(a0, a2) + c1

    lo = a0 * a3
    s = lo + carry
    c1 = if (uLtInline(s, lo)) 1L else 0L
    w.l3 = s
    w.l4 = umulh(a0, a3) + c1

    lo = a1 * a2
    hi = umulh(a1, a2)
    prev = w.l3
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    w.l3 = s
    carry = hi + c1

    lo = a1 * a3
    hi = umulh(a1, a3)
    prev = w.l4
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    s += carry
    c2 = if (uLtInline(s, carry)) 1L else 0L
    w.l4 = s
    w.l5 = hi + c1 + c2

    lo = a2 * a3
    hi = umulh(a2, a3)
    prev = w.l5
    s = prev + lo
    c1 = if (uLtInline(s, prev)) 1L else 0L
    w.l5 = s
    w.l6 = hi + c1

    // Pass 2: double all cross-products (shift left by 1 bit)
    v = w.l1
    w.l1 = v shl 1
    var shiftCarry = v ushr 63
    v = w.l2
    w.l2 = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w.l3
    w.l3 = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w.l4
    w.l4 = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w.l5
    w.l5 = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    v = w.l6
    w.l6 = (v shl 1) or shiftCarry
    shiftCarry = v ushr 63
    w.l7 = shiftCarry

    // Pass 3: add diagonal products a[i]²
    lo = a0 * a0
    hi = umulh(a0, a0)
    w.l0 = lo
    s = w.l1 + hi
    c1 = if (uLtInline(s, w.l1)) 1L else 0L
    w.l1 = s
    var dCarry = c1

    lo = a1 * a1
    hi = umulh(a1, a1)
    s = w.l2 + lo
    c1 = if (uLtInline(s, w.l2)) 1L else 0L
    s += dCarry
    c2 = if (uLtInline(s, dCarry)) 1L else 0L
    w.l2 = s
    prev = w.l3 + hi
    val c3a = if (uLtInline(prev, w.l3)) 1L else 0L
    prev += c1 + c2
    val c4a = if (uLtInline(prev, c1 + c2)) 1L else 0L
    w.l3 = prev
    dCarry = c3a + c4a

    lo = a2 * a2
    hi = umulh(a2, a2)
    s = w.l4 + lo
    c1 = if (uLtInline(s, w.l4)) 1L else 0L
    s += dCarry
    c2 = if (uLtInline(s, dCarry)) 1L else 0L
    w.l4 = s
    prev = w.l5 + hi
    val c3b = if (uLtInline(prev, w.l5)) 1L else 0L
    prev += c1 + c2
    val c4b = if (uLtInline(prev, c1 + c2)) 1L else 0L
    w.l5 = prev
    dCarry = c3b + c4b

    lo = a3 * a3
    hi = umulh(a3, a3)
    s = w.l6 + lo
    c1 = if (uLtInline(s, w.l6)) 1L else 0L
    s += dCarry
    c2 = if (uLtInline(s, dCarry)) 1L else 0L
    w.l6 = s
    prev = w.l7 + hi
    prev += c1 + c2
    w.l7 = prev

    // === Reduction ===
    reduceWideInline(out, w, umulh)
}

/**
 * Inline mod-p reduction of 512-bit value. Shared by fieldMulReduceWith and fieldSqrReduceWith.
 *
 * Uses 2^256 ≡ 2^32 + 977 (mod p) to fold high limbs into low limbs.
 */
@Suppress("LongMethod")
internal inline fun reduceWideInline(
    out: Fe4,
    w: Wide8,
    umulh: (Long, Long) -> Long,
) {
    val c = 4294968273L // 2^32 + 977
    var hcLo: Long
    var hcHi: Long
    var s1: Long
    var s2: Long
    var c1: Long
    var c2: Long

    // Round 1: acc = lo + hi × C
    hcLo = w.l4 * c
    hcHi = umulh(w.l4, c)
    s1 = w.l0 + hcLo
    c1 = if (uLtInline(s1, w.l0)) 1L else 0L
    out.l0 = s1
    var carry = hcHi + c1

    hcLo = w.l5 * c
    hcHi = umulh(w.l5, c)
    s1 = w.l1 + hcLo
    c1 = if (uLtInline(s1, w.l1)) 1L else 0L
    s2 = s1 + carry
    c2 = if (uLtInline(s2, s1)) 1L else 0L
    out.l1 = s2
    carry = hcHi + c1 + c2

    hcLo = w.l6 * c
    hcHi = umulh(w.l6, c)
    s1 = w.l2 + hcLo
    c1 = if (uLtInline(s1, w.l2)) 1L else 0L
    s2 = s1 + carry
    c2 = if (uLtInline(s2, s1)) 1L else 0L
    out.l2 = s2
    carry = hcHi + c1 + c2

    hcLo = w.l7 * c
    hcHi = umulh(w.l7, c)
    s1 = w.l3 + hcLo
    c1 = if (uLtInline(s1, w.l3)) 1L else 0L
    s2 = s1 + carry
    c2 = if (uLtInline(s2, s1)) 1L else 0L
    out.l3 = s2
    carry = hcHi + c1 + c2

    // Round 2: fold carry × C
    if (carry != 0L) {
        val ccLo = carry * c
        val ccHi = umulh(carry, c)
        s1 = out.l0 + ccLo
        c1 = if (uLtInline(s1, out.l0)) 1L else 0L
        out.l0 = s1
        var prop = ccHi + c1
        if (prop != 0L) {
            s1 = out.l1 + prop
            prop = if (uLtInline(s1, out.l1)) 1L else 0L
            out.l1 = s1
            if (prop != 0L) {
                s1 = out.l2 + prop
                prop = if (uLtInline(s1, out.l2)) 1L else 0L
                out.l2 = s1
                if (prop != 0L) {
                    s1 = out.l3 + prop
                    prop = if (uLtInline(s1, out.l3)) 1L else 0L
                    out.l3 = s1
                }
            }
        }
        if (prop != 0L) {
            s1 = out.l0 + c
            c1 = if (uLtInline(s1, out.l0)) 1L else 0L
            out.l0 = s1
            if (c1 != 0L) {
                out.l1++
                if (out.l1 == 0L) {
                    out.l2++
                    if (out.l2 == 0L) out.l3++
                }
            }
        }
    }

    // Final normalization: ensure out < p
    if (out.l3 == -1L && out.l2 == -1L && out.l1 == -1L &&
        (out.l0 xor Long.MIN_VALUE) >= (FIELD_P0 xor Long.MIN_VALUE)
    ) {
        out.l0 -= FIELD_P0
        out.l1 = 0L
        out.l2 = 0L
        out.l3 = 0L
    }
}
