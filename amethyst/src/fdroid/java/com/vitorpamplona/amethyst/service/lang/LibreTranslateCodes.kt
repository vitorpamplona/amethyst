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
package com.vitorpamplona.amethyst.service.lang

/**
 * Pure BCP-47 → LibreTranslate code mapping, kept free of Android/framework
 * dependencies so it can be unit-tested on the JVM.
 */
object LibreTranslateCodes {
    // LibreTranslate language set (ISO 639-1). Codes outside it are skipped.
    val supported: Set<String> =
        setOf(
            "en", "ar", "az", "bg", "bs", "ca", "cs", "da", "de", "el", "es", "et", "fa", "fi",
            "fr", "he", "hi", "hr", "hu", "hy", "id", "it", "ja", "ka", "kk", "ko", "lt", "lv",
            "mk", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sr", "sv",
            "th", "tr", "uk", "ur", "vi", "zh",
        )

    /** Maps a BCP-47 tag (e.g. "zh-CN") to the closest LibreTranslate code, or null. */
    fun toCode(tag: String): String? {
        val code = tag.substringBefore('-').lowercase()
        return code.takeIf { it in supported }
    }
}
