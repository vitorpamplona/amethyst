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
 * Android field multiply/square — uses pure-Kotlin fallback for multiply-high.
 *
 * WHY NOT use Math.unsignedMultiplyHigh (API 35+) or Math.multiplyHigh (API 31+)?
 *   Because D8/R8 desugaring generates a synthetic backport wrapper
 *   (ExternalSyntheticBackport0.m) for ANY Math.xxx call when minSdk < the API
 *   that introduced it (minSdk=26 < 31/35). This backport wrapper:
 *     - Adds 139ns per call (traced on Pixel 8 with Android 16)
 *     - Is called 24,875 times per Schnorr verify
 *     - Costs 3.45ms total = 17.5% of verify time
 *   The pure-Kotlin fallback (4 Long multiplies + shifts) avoids the backport
 *   entirely and is FASTER than the backported intrinsic path.
 *
 * WHY NOT use API-level dispatch (fieldMulApi35/Api31/Fallback)?
 *   Profiling showed the D8 backport overhead dominates. The UMULH/SMULH
 *   intrinsic behind the backport is ~1ns, but the backport wrapper adds ~138ns.
 *   The pure fallback at ~10-20ns is much faster than 1+138=139ns.
 *
 * ALSO: uLt calls accounted for 20.7% of verify time (49,924 calls × 82ns each)
 *   because it's an expect/actual function (can't be inline). The inline XOR
 *   comparison in the crossinline lambda eliminates all those function calls.
 */
internal actual fun fieldMulReduce(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    fieldMulReduceWith(out, a, b, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
}

internal actual fun fieldSqrReduce(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    fieldSqrReduceWith(out, a, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
}
