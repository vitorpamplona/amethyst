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
 * Android field multiply/square — dispatches to the best available intrinsic.
 *
 * Three implementations, selected once at class load:
 *   - API 35+ (Android 15): Math.unsignedMultiplyHigh via MethodHandle → UMULH on ARM64
 *   - API 31+ (Android 12): Math.multiplyHigh via MethodHandle + unsigned correction → SMULH
 *   - Below:  pure-Kotlin fallback (4 Long multiplies + shifts)
 *
 * WHY MethodHandle instead of direct Math.xxx calls?
 *   D8 desugaring replaces ALL direct references to Math.unsignedMultiplyHigh and
 *   Math.multiplyHigh with synthetic backport wrappers (ExternalSyntheticBackport0.m)
 *   when minSdk < the API that introduced them. These backports are pure-Java fallbacks
 *   that never reach the hardware intrinsic — even on devices running API 35+.
 *   MethodHandle.invokeExact uses DEX invoke-polymorphic which D8 cannot desugar,
 *   so ART resolves directly to the real Math method and intrinsifies it.
 *
 * The API level check happens ONCE here, not per-multiply-high call. Each fused
 * fieldMulReduceWith inlines the intrinsic lambda at every call site (~16 per mul),
 * producing tight bytecode with zero per-call dispatch overhead.
 */

private val HAS_UNSIGNED_MULTIPLY_HIGH = android.os.Build.VERSION.SDK_INT >= 35
private val HAS_MULTIPLY_HIGH = android.os.Build.VERSION.SDK_INT >= 31

internal actual fun fieldMulReduce(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    if (HAS_UNSIGNED_MULTIPLY_HIGH) {
        FieldMulApi35.fieldMulReduce(out, a, b, w)
    } else if (HAS_MULTIPLY_HIGH) {
        FieldMulApi31.fieldMulReduce(out, a, b, w)
    } else {
        fieldMulReduceWith(out, a, b, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
    }
}

internal actual fun fieldSqrReduce(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    if (HAS_UNSIGNED_MULTIPLY_HIGH) {
        FieldMulApi35.fieldSqrReduce(out, a, w)
    } else if (HAS_MULTIPLY_HIGH) {
        FieldMulApi31.fieldSqrReduce(out, a, w)
    } else {
        fieldSqrReduceWith(out, a, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
    }
}
