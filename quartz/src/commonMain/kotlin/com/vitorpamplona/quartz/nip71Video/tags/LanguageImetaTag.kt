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
package com.vitorpamplona.quartz.nip71Video.tags

import androidx.compose.runtime.Immutable

/**
 * NIP-71 audio-track language tag (lives inside an `imeta` tag).
 *
 * Format: `l <code> <standard> [ov]`
 *
 * - `code` — language code, e.g. `en`
 * - `standard` — namespace, e.g. `ISO-639-1` (default when missing)
 * - `ov` — optional flag marking the original-version track
 */
@Immutable
data class LanguageImetaTag(
    val code: String,
    val standard: String = DEFAULT_STANDARD,
    val originalVersion: Boolean = false,
) {
    fun toPropertyValue(): String =
        buildString {
            append(code)
            append(' ')
            append(standard)
            if (originalVersion) {
                append(' ')
                append(ORIGINAL_VERSION_MARKER)
            }
        }

    companion object {
        const val TAG_NAME = "l"
        const val DEFAULT_STANDARD = "ISO-639-1"
        const val ORIGINAL_VERSION_MARKER = "ov"

        fun parseValue(value: String): LanguageImetaTag? {
            val parts = value.split(' ').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val code = parts[0]
            val standard = parts.getOrNull(1) ?: DEFAULT_STANDARD
            val ov = parts.drop(2).any { it == ORIGINAL_VERSION_MARKER }
            return LanguageImetaTag(code, standard, ov)
        }
    }
}
