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
 * Android: XOR-with-MIN_VALUE trick for unsigned comparison.
 *
 * DO NOT use Long.compareUnsigned here (even though it's an ART intrinsic since API 26).
 * Kotlin's `a.toULong() < b.toULong()` compiles to Long.compareUnsigned BUT also emits
 * 2 ULong.constructor-impl NOOP invokestatic calls per comparison. These NOOPs add ~2-3ns
 * each on ART, totaling ~35-54μs per verify (~17,800 comparisons).
 *
 * DO NOT use Long.compareUnsigned directly either — ART may or may not inline the function
 * call depending on method size budgets, adding unpredictable overhead.
 *
 * The XOR trick produces pure arithmetic bytecode with ZERO method calls:
 *   Bytecode: lload a, ldc MIN_VALUE, lxor, lload b, ldc MIN_VALUE, lxor, lcmp, ifge
 *   ARM64:    EOR x0, a, #0x8000...; EOR x1, b, #0x8000...; CMP x0, x1; CSET x2, LT
 *
 * This approach was validated by bytecode analysis (javap) and benchmarked on Pixel 8
 * (Android 16): ~14% improvement across all EC point operations.
 */
internal actual fun uLt(
    a: Long,
    b: Long,
): Boolean = (a xor Long.MIN_VALUE) < (b xor Long.MIN_VALUE)
