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
 * Dispatches ONCE per call to an API-level-specific implementation. Each implementation
 * uses fieldMulReduceWith/fieldSqrReduceWith (inline) with the best available intrinsic
 * inlined directly at the call site. This eliminates the per-multiply-high wrapper call
 * overhead that hurts ART's JIT:
 *
 * Before: FieldP.mul → U256.mulWide → unsignedMultiplyHigh (branch) → Math.xxx
 *         = 2 extra function calls per multiply-high × 20 per field mul = 40 calls
 *
 * After:  FieldP.mul → fieldMulReduce → (one of the inlined paths)
 *         = 1 function call total, Math.xxx is inlined directly into the loop
 *
 * The API level check is the outermost branch (not inside the hot loop), so ART
 * profiles and JIT-compiles only the hot path.
 */

private val API = android.os.Build.VERSION.SDK_INT

@Suppress("NewApi")
internal actual fun fieldMulReduce(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    if (API >= 35) {
        fieldMulReduceWith(out, a, b, w) { x, y -> Math.unsignedMultiplyHigh(x, y) }
    } else if (API >= 31) {
        fieldMulReduceWith(out, a, b, w) { x, y ->
            Math.multiplyHigh(x, y) + (x and (y shr 63)) + (y and (x shr 63))
        }
    } else {
        fieldMulReduceWith(out, a, b, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
    }
}

@Suppress("NewApi")
internal actual fun fieldSqrReduce(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    if (API >= 35) {
        fieldSqrReduceWith(out, a, w) { x, y -> Math.unsignedMultiplyHigh(x, y) }
    } else if (API >= 31) {
        fieldSqrReduceWith(out, a, w) { x, y ->
            Math.multiplyHigh(x, y) + (x and (y shr 63)) + (y and (x shr 63))
        }
    } else {
        fieldSqrReduceWith(out, a, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
    }
}
