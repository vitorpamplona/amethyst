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
 * Each API level gets its OWN private function with the inline expansion,
 * so ART's JIT can fully optimize each as a standalone ~200 DEX-instruction
 * method. Previously, having all 3 branches inline-expanded in a single
 * function created ~600 DEX instructions, causing ART's register allocator
 * to produce suboptimal code with excessive stack spills.
 *
 * The dispatch functions (fieldMulReduce/fieldSqrReduce) are tiny (~15 DEX
 * instructions) and always take the same branch, so ART profiles and
 * devirtualizes them efficiently.
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
