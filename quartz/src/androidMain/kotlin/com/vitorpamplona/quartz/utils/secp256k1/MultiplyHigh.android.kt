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
 * Android implementation with API-level-gated intrinsics.
 *
 * Resolved once at class init to avoid per-call branch overhead:
 * - API 31+ (Android 12): Math.multiplyHigh (ART intrinsic → SMULH on ARM64)
 * - API 35+ (Android 15): Math.unsignedMultiplyHigh (ART intrinsic → UMULH on ARM64)
 * - Below: pure-Kotlin fallback via four 32-bit sub-products
 *
 * These functions are called 16× per field multiply (~12,000× per signature verify),
 * so eliminating the per-call version check is critical.
 *
 * Implementation strategy is resolved once at class load time via static finals.
 * The JIT then devirtualizes and inlines the hot path.
 */

private val HAS_MULTIPLY_HIGH = android.os.Build.VERSION.SDK_INT >= 31
private val HAS_UNSIGNED_MULTIPLY_HIGH = android.os.Build.VERSION.SDK_INT >= 35

internal actual fun multiplyHigh(
    a: Long,
    b: Long,
): Long =
    if (HAS_MULTIPLY_HIGH) {
        Math.multiplyHigh(a, b)
    } else {
        multiplyHighFallback(a, b)
    }

internal actual fun unsignedMultiplyHigh(
    a: Long,
    b: Long,
): Long =
    if (HAS_UNSIGNED_MULTIPLY_HIGH) {
        @Suppress("NewApi")
        Math.unsignedMultiplyHigh(a, b)
    } else if (HAS_MULTIPLY_HIGH) {
        // API 31-34: signed multiplyHigh + unsigned correction (3 extra insns)
        Math.multiplyHigh(a, b) + (a and (b shr 63)) + (b and (a shr 63))
    } else {
        // API <31: pure-Kotlin fallback
        unsignedMultiplyHighFallback(a, b)
    }
