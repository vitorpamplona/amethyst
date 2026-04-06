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
 * JVM implementation using Math.multiplyHigh (Java 9+).
 * The HotSpot JIT compiles this to a single hardware instruction:
 * IMULH on x86-64, SMULH on ARM64.
 */
internal actual fun multiplyHigh(
    a: Long,
    b: Long,
): Long = Math.multiplyHigh(a, b)

/**
 * JVM: uses Math.unsignedMultiplyHigh (Java 18+) if available, else fallback.
 * The HAS_UNSIGNED_MULTIPLY_HIGH check is evaluated once at class init and the
 * JIT will devirtualize the hot branch after a few invocations.
 */
internal actual fun unsignedMultiplyHigh(
    a: Long,
    b: Long,
): Long =
    if (HAS_UNSIGNED_MULTIPLY_HIGH) {
        unsignedMultiplyHighNative(a, b)
    } else {
        unsignedMultiplyHighFallback(a, b)
    }

/**
 * Tries to resolve Math.unsignedMultiplyHigh (Java 18+) as a MethodHandle.
 * If available, invoking it compiles to a single UMULH instruction, saving
 * 4 correction instructions per product vs the signed multiplyHigh + fixup path.
 * This saves ~64 instructions per field multiplication (16 products × 4 insns).
 *
 * MethodHandle.invokeExact is JIT-inlined to the same cost as a direct call.
 */
private val UNSIGNED_MUL_HIGH: java.lang.invoke.MethodHandle? =
    try {
        java.lang.invoke.MethodHandles.lookup().findStatic(
            Math::class.java,
            "unsignedMultiplyHigh",
            java.lang.invoke.MethodType.methodType(
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
            ),
        )
    } catch (_: Throwable) {
        null
    }

/** True if the native unsigned multiply high is available (Java 18+). */
internal val HAS_UNSIGNED_MULTIPLY_HIGH: Boolean = UNSIGNED_MUL_HIGH != null

/** Call Math.unsignedMultiplyHigh via MethodHandle (only when HAS_UNSIGNED_MULTIPLY_HIGH is true). */
internal fun unsignedMultiplyHighNative(
    a: Long,
    b: Long,
): Long = UNSIGNED_MUL_HIGH!!.invokeExact(a, b) as Long
