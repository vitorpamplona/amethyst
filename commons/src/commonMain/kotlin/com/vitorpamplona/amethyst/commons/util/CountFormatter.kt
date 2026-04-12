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
package com.vitorpamplona.amethyst.commons.util

import kotlin.math.roundToInt

/**
 * Formats an integer count to a human-readable abbreviated string.
 *
 * Examples:
 * - null -> ""
 * - 0 -> ""
 * - 999 -> "999"
 * - 10000 -> "10k"
 * - 1500000 -> "2M"
 * - 2000000000 -> "2G"
 */
fun showCount(count: Int?): String {
    if (count == null) return ""
    if (count == 0) return ""

    return when {
        count >= 1_000_000_000 -> "${(count / 1_000_000_000f).roundToInt()}G"
        count >= 1_000_000 -> "${(count / 1_000_000f).roundToInt()}M"
        count >= 10_000 -> "${(count / 1_000f).roundToInt()}k"
        else -> "$count"
    }
}
