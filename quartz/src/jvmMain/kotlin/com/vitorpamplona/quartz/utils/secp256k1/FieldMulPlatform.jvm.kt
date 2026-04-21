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
 * JVM field multiply/square — delegates to the original unfused path.
 *
 * WHY NOT use the fused fieldMulReduceWith (like Android)?
 *   The fused inline+crossinline approach was designed for ART's limited inlining.
 *   On HotSpot C2, it produces a 2351-bytecode method (with 180 wasted bytecodes
 *   from lambda parameter shuffling) that exceeds FreqInlineSize (325), preventing
 *   HotSpot from inlining fieldMulReduce into FieldP.mul.
 *
 *   The unfused path (mulWide + reduceWide) produces smaller methods (~320 bytecodes
 *   each) that HotSpot inlines individually and optimizes across boundaries. HotSpot's
 *   8+ level inlining depth handles the full chain:
 *     FieldP.mul → fieldMulReduce → U256.mulWide → unsignedMultiplyHigh → Math.unsignedMultiplyHigh
 *   Each Math.unsignedMultiplyHigh is a C2 intrinsic → single MULQ on x86-64.
 *
 *   Benchmark confirmed: unfused path is equal or faster on HotSpot JVM 21.
 */
internal actual fun fieldMulReduce(
    out: Fe4,
    a: Fe4,
    b: Fe4,
    w: Wide8,
) {
    U256.mulWide(w, a, b)
    FieldP.reduceWide(out, w)
}

internal actual fun fieldSqrReduce(
    out: Fe4,
    a: Fe4,
    w: Wide8,
) {
    U256.sqrWide(w, a)
    FieldP.reduceWide(out, w)
}
