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
package com.vitorpamplona.quartz.nipCCGeocaching.listing

/**
 * ROT13, the encoding geocaching has always used to keep a hint from spoiling itself.
 *
 * NIP-CC asks clients to "support hint encoding, such as ROT13, to prevent spoilers". The `hint`
 * tag itself travels as plaintext, so this is a display transform: show the rotated text, and
 * rotate it back when the reader asks for it. Rotating twice returns the original, so one
 * function serves both directions.
 *
 * Only ASCII letters move; digits, punctuation and every non-ASCII letter are left alone, which
 * is what every other geocaching implementation does and what makes the round trip exact.
 */
fun rot13(text: String): String =
    text
        .map { char ->
            when (char) {
                in 'a'..'z' -> 'a' + ((char - 'a') + 13) % 26
                in 'A'..'Z' -> 'A' + ((char - 'A') + 13) % 26
                else -> char
            }
        }.joinToString("")
