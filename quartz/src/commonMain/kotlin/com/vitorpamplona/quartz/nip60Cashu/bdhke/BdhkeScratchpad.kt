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
package com.vitorpamplona.quartz.nip60Cashu.bdhke

import com.vitorpamplona.quartz.utils.secp256k1.Fe4
import com.vitorpamplona.quartz.utils.secp256k1.MutablePoint

/**
 * Pre-allocated holders the BDHKE crypto primitives re-use across calls.
 *
 * # Why
 *
 * Android 15+ ART JIT compiler crashes (SIGSEGV at offset 0x48 in
 * "Jit thread pool") when it escape-analyzes a method body that
 * allocates many short-lived [Fe4] / [MutablePoint] holders in tight
 * sequence. [Bdhke.unblind] used to do that — about 10 such
 * allocations per call. Splitting into smaller methods didn't help
 * (ART's inliner re-merges them at compile time).
 *
 * Passing the holders in as parameters dodges the bug. The JIT sees
 * the objects come from outside, so its (buggy) scalarization pass
 * doesn't run on them. Side benefit: a hot loop like NUT-09 restore
 * (hundreds of unblinds per sweep) now does one [BdhkeScratchpad]
 * allocation total instead of ~10 per iteration.
 *
 * # Safety
 *
 * All holders are fully overwritten on each crypto call —
 * `KeyCodec.parsePublicKey`, `U256.fromBytesInto`, `ECPoint.mul`,
 * `ECPoint.toAffine`, `FieldP.neg`, `MutablePoint.setAffine` all
 * write through every field they use. So reusing one scratchpad
 * across many sequential calls in the same thread is safe even if
 * earlier content was different.
 *
 * # Thread safety
 *
 * [BdhkeScratchpad] is NOT thread-safe. One scratchpad per caller
 * is the contract. Two coroutines on different threads must each
 * allocate their own. The cost is negligible (~10 small objects).
 */
class BdhkeScratchpad {
    // Holders used by [Bdhke.unblind].
    internal val fe4A: Fe4 = Fe4()
    internal val fe4B: Fe4 = Fe4()
    internal val fe4C: Fe4 = Fe4()
    internal val fe4D: Fe4 = Fe4()
    internal val fe4E: Fe4 = Fe4()
    internal val fe4F: Fe4 = Fe4()
    internal val fe4G: Fe4 = Fe4()
    internal val pointA: MutablePoint = MutablePoint()
    internal val pointB: MutablePoint = MutablePoint()
    internal val pointC: MutablePoint = MutablePoint()
    internal val pointD: MutablePoint = MutablePoint()
    internal val outPoint: MutablePoint = MutablePoint()

    // Holders used by [Bdhke.blind] and [Bdhke.hashToCurve].
    // Kept distinct from the unblind holders so a future refactor that
    // calls blind from inside unblind (or vice versa) doesn't silently
    // overwrite live state. Currently the operations don't nest, but
    // the separation makes the safety invariant local.
    internal val blindFe4X: Fe4 = Fe4()
    internal val blindFe4Y: Fe4 = Fe4()
    internal val blindFe4Scalar: Fe4 = Fe4()
    internal val blindPointY: MutablePoint = MutablePoint()
    internal val blindPointRg: MutablePoint = MutablePoint()
    internal val blindPointOut: MutablePoint = MutablePoint()

    // Holders used by [Bdhke.verifyDleq] + [Bdhke.verifyDleqCarol] +
    // [Bdhke.addRTimesA]. The Carol path runs once per inbound nutzap
    // or cashu-token proof during redeem, so it lives on the hot
    // path of receive flows.
    internal val verifyFe4E: Fe4 = Fe4()
    internal val verifyFe4S: Fe4 = Fe4()
    internal val verifyFe4Ax: Fe4 = Fe4()
    internal val verifyFe4Ay: Fe4 = Fe4()
    internal val verifyFe4Bx: Fe4 = Fe4()
    internal val verifyFe4By: Fe4 = Fe4()
    internal val verifyFe4Cx: Fe4 = Fe4()
    internal val verifyFe4Cy: Fe4 = Fe4()
    internal val verifyFe4Ser: Fe4 = Fe4()
    internal val verifyFe4Ser2: Fe4 = Fe4()
    internal val verifyPointA: MutablePoint = MutablePoint()
    internal val verifyPointB: MutablePoint = MutablePoint()
    internal val verifyPointC: MutablePoint = MutablePoint()
    internal val verifyPointSg: MutablePoint = MutablePoint()
    internal val verifyPointEa: MutablePoint = MutablePoint()
    internal val verifyPointNegEa: MutablePoint = MutablePoint()
    internal val verifyPointR1: MutablePoint = MutablePoint()
    internal val verifyPointSb: MutablePoint = MutablePoint()
    internal val verifyPointEc: MutablePoint = MutablePoint()
    internal val verifyPointNegEc: MutablePoint = MutablePoint()
    internal val verifyPointR2: MutablePoint = MutablePoint()
    internal val verifyPointTmp: MutablePoint = MutablePoint()

    // Holders used by [Bdhke.toCompressed] (and its OrNull variant).
    // These are reused at the END of every blind / unblind / addRTimesA
    // call to convert the result MutablePoint back into a 33-byte
    // compressed ByteArray. Hot loops (NUT-09 restore, NUT-07 scrub)
    // call this hundreds of times, so the per-call 2-Fe4 allocation
    // adds up to the JIT-bug threshold once ART inlines it into the
    // outer crypto bodies.
    internal val toCompressedFe4X: Fe4 = Fe4()
    internal val toCompressedFe4Y: Fe4 = Fe4()
}
