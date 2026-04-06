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
 * Returns the upper 64 bits of the signed 128-bit product of two Long values.
 *
 * On JVM (Java 9+), this maps to Math.multiplyHigh which the JIT compiles to
 * a single SMULH (ARM64) or IMUL (x86-64) instruction. On other platforms,
 * a pure-Kotlin fallback computes it via four 32-bit sub-products.
 */
internal expect fun multiplyHigh(
    a: Long,
    b: Long,
): Long

/**
 * Returns the upper 64 bits of the UNSIGNED 128-bit product of two Long values.
 *
 * On JVM 18+, delegates to Math.unsignedMultiplyHigh (single UMULH instruction).
 * On older JVMs and other platforms, uses signed multiplyHigh with sign-bit correction.
 * The correction adds 4 instructions per call; eliminating it saves ~64 insns per field mul.
 */
internal expect fun unsignedMultiplyHigh(
    a: Long,
    b: Long,
): Long

/**
 * Fallback: unsigned multiply high from signed multiply high + correction.
 */
internal fun unsignedMultiplyHighFallback(
    a: Long,
    b: Long,
): Long = multiplyHigh(a, b) + (a and (b shr 63)) + (b and (a shr 63))

/**
 * Pure-Kotlin fallback for multiplyHigh, using four 32-bit sub-products.
 * Used on platforms where no hardware intrinsic is available.
 */
internal fun multiplyHighFallback(
    a: Long,
    b: Long,
): Long {
    val aLo = a and 0xFFFFFFFFL
    val aHi = a ushr 32
    val bLo = b and 0xFFFFFFFFL
    val bHi = b ushr 32

    val mid1 = aHi * bLo
    val mid2 = aLo * bHi
    val low = aLo * bLo

    // 1. Calculate the carry from the lower 64 bits to the upper 64 bits
    val carry = ((low ushr 32) + (mid1 and 0xFFFFFFFFL) + (mid2 and 0xFFFFFFFFL)) ushr 32

    // 2. Base result (unsigned multiplication of the components)
    var result = (aHi * bHi) + (mid1 ushr 32) + (mid2 ushr 32) + carry

    // 3. The Fix: Apply corrections for signed numbers
    // If a is negative, subtract b from the high 64 bits
    if (a < 0) result -= b
    // If b is negative, subtract a from the high 64 bits
    if (b < 0) result -= a

    return result
}
