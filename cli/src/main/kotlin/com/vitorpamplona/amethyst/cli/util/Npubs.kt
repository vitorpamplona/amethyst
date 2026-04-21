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
package com.vitorpamplona.amethyst.cli.util

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes

/** Accept either an `npub1…` or a raw 64-hex pubkey; always return hex. */
object Npubs {
    fun resolveToHex(input: String): String {
        val trimmed = input.trim()
        // Already hex?
        if (trimmed.length == 64 && trimmed.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return trimmed.lowercase()
        }
        if (trimmed.startsWith("npub1") || trimmed.startsWith("npub")) {
            return trimmed.bechToBytes().toHexKey()
        }
        throw IllegalArgumentException("expected npub or 64-hex pubkey, got: $input")
    }
}
