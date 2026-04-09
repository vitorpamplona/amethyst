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
 * JVM: Long.compareUnsigned is a HotSpot C2 JIT intrinsic.
 *
 * DO NOT replace with the XOR trick `(a xor MIN_VALUE) < (b xor MIN_VALUE)`.
 * HotSpot recognizes Long.compareUnsigned and compiles it to a single unsigned
 * CMP + SETB instruction pair. The XOR trick generates 2 extra XOR instructions
 * that HotSpot does NOT optimize away, causing a ~30% regression on verify
 * (1.6× → 2.1× vs native, measured on JDK 21 x86-64).
 *
 * This is the opposite of Android/ART where the XOR trick is faster because
 * it avoids ULong.constructor-impl overhead. See UnsignedCompare.android.kt.
 */
internal actual fun uLt(
    a: Long,
    b: Long,
): Boolean = java.lang.Long.compareUnsigned(a, b) < 0
