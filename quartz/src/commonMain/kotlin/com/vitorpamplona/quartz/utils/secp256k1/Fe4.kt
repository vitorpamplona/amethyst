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

import kotlin.jvm.JvmField

// =====================================================================================
// STRUCT-BASED FIELD ELEMENT: 4 named Long fields instead of LongArray(4).
//
// MOTIVATION:
//   LongArray(4) requires a bounds check on every laload/lastore instruction in JVM
//   bytecode. Even though HotSpot C2 can often eliminate these for constant indices
//   when the array size is known, the situation is worse on:
//     - Android ART: Limited inlining depth means fewer bounds checks eliminated.
//     - Kotlin/Native LLVM AOT: Parameter arrays are harder to analyze statically.
//
//   With named fields (l0..l3), access compiles to direct getfield/putfield bytecode
//   on JVM, and direct struct field access on Native — zero bounds checks on any
//   platform, ever.
//
// MEMORY:
//   LongArray(4): 12-byte header + 4-byte length + 32 bytes data = 48 bytes
//   Fe4 object:   16-byte header + 32 bytes data = 48 bytes  (identical)
//
// BYTECODE COMPARISON (per field access):
//   LongArray: aload + iconst + laload  (3 insns + implicit bounds check)
//   Fe4:       aload + getfield          (2 insns, no check)
//
// =====================================================================================

/**
 * Mutable 256-bit field element using 4 named Long fields in little-endian order.
 * l0 = least significant 64 bits, l3 = most significant 64 bits.
 *
 * @JvmField eliminates virtual getter/setter generation — direct field access.
 */
internal class Fe4(
    @JvmField var l0: Long = 0L,
    @JvmField var l1: Long = 0L,
    @JvmField var l2: Long = 0L,
    @JvmField var l3: Long = 0L,
) {
    fun isZero(): Boolean = (l0 or l1 or l2 or l3) == 0L

    fun copyFrom(other: Fe4) {
        l0 = other.l0
        l1 = other.l1
        l2 = other.l2
        l3 = other.l3
    }

    fun setZero() {
        l0 = 0L
        l1 = 0L
        l2 = 0L
        l3 = 0L
    }
}

/**
 * Mutable 512-bit wide product buffer using 8 named Long fields.
 * Used for intermediate results of 256×256-bit multiplication before reduction.
 *
 * Replaces LongArray(8) scratch buffers — eliminates 8 bounds checks per access.
 */
internal class Wide8(
    @JvmField var l0: Long = 0L,
    @JvmField var l1: Long = 0L,
    @JvmField var l2: Long = 0L,
    @JvmField var l3: Long = 0L,
    @JvmField var l4: Long = 0L,
    @JvmField var l5: Long = 0L,
    @JvmField var l6: Long = 0L,
    @JvmField var l7: Long = 0L,
)
