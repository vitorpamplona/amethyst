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
 * Android field multiply/square with API-level-gated intrinsics.
 *
 * ARCHITECTURE: dispatch → per-API private function → inline-expanded hot loop
 *
 * Each API level gets its OWN private function (fieldMulApi35, fieldMulApi31,
 * fieldMulFallback) with the fieldMulReduceWith inline expansion. This is critical
 * because ART's JIT optimizes methods individually:
 *
 * WHY separate private functions per API level?
 *   Tested: putting all 3 branches in a single fieldMulReduce with inline expansion
 *   created ~600 DEX instructions (3 copies × ~200 each). ART's register allocator
 *   produced suboptimal code — verify REGRESSED by ~4% vs baseline. With separate
 *   functions, each is ~200 DEX instructions, well within ART's optimization sweet spot.
 *   Verify improved by 16% instead.
 *
 * WHY the API-level split at all?
 *   - API 35+ (Android 15): Math.unsignedMultiplyHigh → UMULH (1 ARM64 instruction)
 *   - API 31-34 (Android 12-14): Math.multiplyHigh → SMULH + 3 correction insns
 *   - API <31 (Android <12): pure-Kotlin fallback (4 multiplies + shifts)
 *   The dispatch function (fieldMulReduce) checks API once, not per multiply-high call.
 *   ART profiles and devirtualizes — on any given device, only one path is hot.
 *
 * WHY NOT use the unsignedMultiplyHigh expect/actual wrapper directly?
 *   The wrapper checks API level on EVERY call (16-20× per field multiply). Even though
 *   ART should constant-fold the check, the wrapper's 3-branch body may exceed ART's
 *   inline-candidate threshold, leaving ~10,000 un-inlined function calls per verify.
 *   The fused approach eliminates the wrapper entirely.
 */

private val API = android.os.Build.VERSION.SDK_INT

// ==================== Multiply: separate methods per API level ====================

@Suppress("NewApi")
private fun fieldMulApi35(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    fieldMulReduceWith(out, a, b, w) { x, y -> Math.unsignedMultiplyHigh(x, y) }
}

private fun fieldMulApi31(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    fieldMulReduceWith(out, a, b, w) { x, y ->
        Math.multiplyHigh(x, y) + (x and (y shr 63)) + (y and (x shr 63))
    }
}

private fun fieldMulFallback(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    fieldMulReduceWith(out, a, b, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
}

@Suppress("NewApi")
internal actual fun fieldMulReduce(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    if (API >= 35) {
        fieldMulApi35(out, a, b, w)
    } else if (API >= 31) {
        fieldMulApi31(out, a, b, w)
    } else {
        fieldMulFallback(out, a, b, w)
    }
}

// ==================== Square: separate methods per API level ====================

@Suppress("NewApi")
private fun fieldSqrApi35(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    fieldSqrReduceWith(out, a, w) { x, y -> Math.unsignedMultiplyHigh(x, y) }
}

private fun fieldSqrApi31(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    fieldSqrReduceWith(out, a, w) { x, y ->
        Math.multiplyHigh(x, y) + (x and (y shr 63)) + (y and (x shr 63))
    }
}

private fun fieldSqrFallback(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    fieldSqrReduceWith(out, a, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
}

@Suppress("NewApi")
internal actual fun fieldSqrReduce(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    if (API >= 35) {
        fieldSqrApi35(out, a, w)
    } else if (API >= 31) {
        fieldSqrApi31(out, a, w)
    } else {
        fieldSqrFallback(out, a, w)
    }
}
