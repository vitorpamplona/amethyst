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
package com.vitorpamplona.quartz.utils

/**
 * Constant-time byte-array equality for MAC / authentication-tag comparison.
 *
 * Unlike [ByteArray.contentEquals], this does not short-circuit on the first
 * differing byte, so its running time does not depend on how many leading bytes
 * of a candidate MAC/tag are correct. Comparing MACs with an early-exit check
 * exposes a timing side channel that can, in principle, let an attacker recover a
 * valid tag byte-by-byte and forge messages (the classic Keyczar/XBox-360 class
 * of bug). Use this for every secret-dependent equality check (HMAC tags, Poly1305
 * tags, reset tokens); ordinary non-secret comparisons can keep `contentEquals`.
 *
 * The length check leaks only the (public, fixed) tag length, not its contents.
 */
fun ByteArray.equalsConstantTime(other: ByteArray): Boolean {
    if (this.size != other.size) return false
    var diff = 0
    for (i in indices) {
        diff = diff or (this[i].toInt() xor other[i].toInt())
    }
    return diff == 0
}
