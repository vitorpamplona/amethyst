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
package com.vitorpamplona.amethyst.desktop.account

private val HEX_64_REGEX = Regex("^[0-9a-fA-F]{64}$")

fun validateBunkerUri(input: String): String? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("bunker://", ignoreCase = true)) return "Not a bunker URI"

    val afterScheme = trimmed.substring("bunker://".length)
    val parts = afterScheme.split("?", limit = 2)
    val pubkeyPart = parts[0]

    if (pubkeyPart.length != 64 || !pubkeyPart.matches(HEX_64_REGEX)) {
        return "Invalid bunker URI. Expected: bunker://<64-hex-chars>?relay=wss://..."
    }

    if (parts.size < 2 || !parts[1].contains("relay=wss://", ignoreCase = true)) {
        return "Bunker URI must include at least one relay parameter (relay=wss://...)"
    }

    return null // valid
}
