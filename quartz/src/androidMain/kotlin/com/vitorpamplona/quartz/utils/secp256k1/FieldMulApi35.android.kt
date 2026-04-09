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
 * API 35+ field multiply/square — PLACEHOLDER for Math.unsignedMultiplyHigh intrinsic.
 *
 * Currently uses the pure-Kotlin fallback because both known approaches to reach
 * the hardware UMULH intrinsic from Kotlin are blocked:
 *
 * 1. Direct call: D8 desugaring replaces Math.unsignedMultiplyHigh with a pure-Java
 *    synthetic backport (ExternalSyntheticBackport0.m) when minSdk < 35. The backport
 *    never reaches the hardware intrinsic — verified via dexdump.
 *
 * 2. MethodHandle.invokeExact: Kotlin cannot generate type-exact invoke-polymorphic
 *    with primitive signature (JJ)J. Only Java's javac has @PolymorphicSignature
 *    support. Kotlin compiles invokeExact as regular invokevirtual with Object[]
 *    boxing — 3 allocations per call × 21 calls per field mul.
 *
 * TO UNLOCK UMULH: Add a Java helper class (MulHighInvoker.java) in a separate
 * Android module or via AGP's Java source set support. The Java class would call
 * MethodHandle.invokeExact(long, long) → long with zero boxing, producing
 * invoke-polymorphic that D8 cannot desugar and ART intrinsifies to UMULH.
 */
internal object FieldMulApi35 {
    fun fieldMulReduce(
        out: LongArray,
        a: LongArray,
        b: LongArray,
        w: LongArray,
    ) {
        fieldMulReduceWith(out, a, b, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
    }

    fun fieldSqrReduce(
        out: LongArray,
        a: LongArray,
        w: LongArray,
    ) {
        fieldSqrReduceWith(out, a, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
    }
}
