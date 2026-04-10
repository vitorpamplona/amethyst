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
 * Native fused field multiply/square using pure-Kotlin fallback.
 *
 * Uses fieldMulReduceFused which computes both lo and hi from shared 32-bit
 * sub-products (4 IMUL per 128-bit product instead of 5). This is the optimal
 * approach for Kotlin/Native because:
 *
 * 1. K/N has no way to emit the hardware MUL instruction (64×64→128) from
 *    Kotlin code. Long * Long only gives the lower 64 bits (IMUL).
 *
 * 2. C interop was benchmarked and adds ~15ns per call through the K/N bridge
 *    layer. With 20 multiply-high calls per field multiply, the bridge overhead
 *    (~300ns) far exceeds the savings from hardware MUL (~150ns saved).
 *
 * 3. The fused mulFull approach (FieldMulFused.kt) computes both lo and hi
 *    from 4 shared sub-products, saving 1 IMUL per product vs separate
 *    lo (IMUL) + hi (4 IMUL fallback) = 5 IMUL.
 *
 * Measured: 44 ns/op on linuxX64 (pure Kotlin fused) vs 331 ns/op (C interop).
 */
internal actual fun fieldMulReduce(
    out: Fe4,
    a: Fe4,
    b: Fe4,
    w: Wide8,
) {
    fieldMulReduceFused(out, a, b, w)
}

internal actual fun fieldSqrReduce(
    out: Fe4,
    a: Fe4,
    w: Wide8,
) {
    fieldSqrReduceFused(out, a, w)
}
