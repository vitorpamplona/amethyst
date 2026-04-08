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
 * JVM fused field multiply/square using Math.unsignedMultiplyHigh (Java 18+).
 * HotSpot C2 already inlines unsignedMultiplyHigh well, but the fused path
 * still helps by eliminating the mulWide → reduceWide call boundary.
 */
internal actual fun fieldMulReduce(
    out: LongArray,
    a: LongArray,
    b: LongArray,
    w: LongArray,
) {
    fieldMulReduceWith(out, a, b, w) { x, y -> Math.unsignedMultiplyHigh(x, y) }
}

internal actual fun fieldSqrReduce(
    out: LongArray,
    a: LongArray,
    w: LongArray,
) {
    fieldSqrReduceWith(out, a, w) { x, y -> Math.unsignedMultiplyHigh(x, y) }
}
