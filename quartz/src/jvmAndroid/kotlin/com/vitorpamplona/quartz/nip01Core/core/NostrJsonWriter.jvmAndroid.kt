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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.crypto.sha2.threadLocalDigest

private val threadWriter = ThreadLocal.withInitial { NostrJsonWriter() }

internal actual fun threadNostrJsonWriter(): NostrJsonWriter? = threadWriter.get()

// String.getBytes(int, int, byte[], int) keeps the low byte of each char, which for ASCII is
// the UTF-8 byte, and copies a Latin-1 string's backing array in one go.
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "DEPRECATION")
internal actual fun copyAsciiInto(
    s: String,
    start: Int,
    end: Int,
    dest: ByteArray,
    at: Int,
) {
    (s as java.lang.String).getBytes(start, end, dest, at)
}

// MessageDigest.digest() resets it for the next caller on this thread.
internal actual fun sha256Of(
    data: ByteArray,
    length: Int,
): ByteArray {
    val digest = threadLocalDigest.get()!!
    digest.update(data, 0, length)
    return digest.digest()
}
