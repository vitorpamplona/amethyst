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
 * Uses the pure-Kotlin fused fallback for all API levels.
 *
 * THREE approaches to reach hardware UMULH were tested and all failed:
 *
 * 1. Direct Math.unsignedMultiplyHigh: D8 replaces with pure-Java backport
 *    when app minSdk=26 < 35, even inside libraries with minSdk=35.
 *
 * 2. MethodHandle.invokeExact (crypto-intrinsics Java module): Bytecode correct
 *    (invoke-polymorphic JJ→J, zero boxing), but ART can't inline through
 *    invoke-polymorphic. 25ns/call × 12K calls = 2.2x regression.
 *
 * 3. Full fieldMulReduce in Java with minSdk=35 module: D8 desugaring runs
 *    at APP level with app's minSdk=26, not library's minSdk=35. All
 *    Math.unsignedMultiplyHigh calls still get backported. Plus Java's
 *    Long.compareUnsigned (282ns/call) is slower than Kotlin's inlined XOR trick.
 *
 * The Kotlin inline+crossinline pattern produces the best ART code:
 * - unsignedMultiplyHighFallback is inlined at each call site (zero dispatch)
 * - uLtInline uses XOR+compare (zero method call, ~0ns overhead)
 * - The fused function stays within ART's inlining budget
 */
internal actual fun fieldMulReduce(
    out: Fe4,
    a: Fe4,
    b: Fe4,
    w: Wide8,
) {
    fieldMulReduceWith(out, a, b, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
}

internal actual fun fieldSqrReduce(
    out: Fe4,
    a: Fe4,
    w: Wide8,
) {
    fieldSqrReduceWith(out, a, w) { x, y -> unsignedMultiplyHighFallback(x, y) }
}
