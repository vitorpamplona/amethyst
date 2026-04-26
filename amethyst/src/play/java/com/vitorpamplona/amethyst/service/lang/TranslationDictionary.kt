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

import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import java.util.regex.Pattern

/**
 * Pure-JVM helpers that protect non-translatable substrings (URLs, Lightning invoices, NIP-19
 * references, NIP-08 positional references) by swapping them with single Unicode Private Use Area
 * codepoints around a translator. Extracted out of [LanguageTranslatorService] so the round-trip
 * can be unit-tested without ML Kit / Android runtime.
 */
internal object TranslationDictionary {
    // Range U+E000..U+F8FF gives 6400 placeholder slots. PUA codepoints don't appear in normal
    // user text, the translator has no rule for them so it passes them through, and using one
    // codepoint per placeholder means the translator can't split or reorder it.
    const val PLACEHOLDER_BASE: Int = 0xE000
    const val PLACEHOLDER_LIMIT: Int = 0xF8FF - PLACEHOLDER_BASE

    // Texts shorter than this, or with no letter codepoints (emoji-only, punctuation), are skipped
    // before any ML Kit work — language identification is unreliable on them.
    private const val MIN_TRANSLATABLE_LENGTH = 4

    val lnRegex: Pattern = Pattern.compile("\\blnbc[a-z0-9]+\\b", Pattern.CASE_INSENSITIVE)
    val tagRegex: Pattern =
        Pattern.compile(
            "(nostr:)?@?(nsec1|npub1|nevent1|naddr1|note1|nprofile1|nrelay1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)",
            Pattern.CASE_INSENSITIVE,
        )

    // Legacy NIP-08 positional references like #[0]. Translators tend to insert a space inside the
    // brackets ("# [0]"), so we shield them via the placeholder dictionary.
    val nip08RefRegex: Pattern = Pattern.compile("#\\[\\d+]")

    fun isWorthTranslating(text: String): Boolean {
        if (text.length < MIN_TRANSLATABLE_LENGTH) return false
        for (cp in text.codePoints()) {
            if (Character.isLetter(cp)) return true
        }
        return false
    }

    fun build(text: String): Map<String, String> {
        val dict = LinkedHashMap<String, String>()
        var counter = 0

        fun addUnique(value: String) {
            if (value.isEmpty()) return
            if (counter > PLACEHOLDER_LIMIT) return
            if (dict.containsValue(value)) return
            dict[placeholder(counter++)] = value
        }

        val lnMatcher = lnRegex.matcher(text)
        while (lnMatcher.find()) addUnique(lnMatcher.group())

        val tagMatcher = tagRegex.matcher(text)
        while (tagMatcher.find()) addUnique(tagMatcher.group())

        val nip08Matcher = nip08RefRegex.matcher(text)
        while (nip08Matcher.find()) addUnique(nip08Matcher.group())

        for (url in UrlDetector(text).detect()) {
            val original = url.originalUrl
            // The URL detector greedily includes Chinese full-width punctuation; skip those false hits.
            if (original.contains('，') || original.contains('。')) continue
            addUnique(original)
        }

        return dict
    }

    fun encode(
        text: String,
        dict: Map<String, String>,
    ): String {
        if (dict.isEmpty()) return text
        var newText = text
        // Replace longest values first so a URL prefix never clobbers a longer URL or tag.
        for ((token, original) in dict.entries.sortedByDescending { it.value.length }) {
            newText = newText.replace(original, token, ignoreCase = false)
        }
        return newText
    }

    fun decode(
        text: String?,
        dict: Map<String, String>,
    ): String? {
        if (text == null || dict.isEmpty()) return text
        var newText: String = text
        for ((token, original) in dict) {
            newText = newText.replace(token, original, ignoreCase = false)
        }
        return newText
    }

    fun placeholder(index: Int): String {
        require(index in 0..PLACEHOLDER_LIMIT) { "placeholder index $index out of range" }
        return String(Character.toChars(PLACEHOLDER_BASE + index))
    }
}
