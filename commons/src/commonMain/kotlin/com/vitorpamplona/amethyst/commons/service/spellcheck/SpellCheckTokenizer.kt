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
package com.vitorpamplona.amethyst.commons.service.spellcheck

import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector

/** A word worth checking: [start] inclusive, [end] exclusive, in the text it was cut from. */
data class WordRange(
    val start: Int,
    val end: Int,
)

/**
 * Cuts a note or message into the words a spelling engine should judge, and leaves out the parts
 * of a Nostr post that are not prose: links, `nostr:` references and bare bech32 keys, hashtags,
 * `@` mentions, `:emoji:` shortcodes, e-mail addresses, Lightning invoices, and code.
 *
 * Within the prose it also leaves out what a dictionary would only get wrong: words with digits
 * ("h4x0r", "2nd"), all-caps acronyms, camelCase and snake_case identifiers, and single letters.
 */
object SpellCheckTokenizer {
    private val skipPatterns =
        listOf(
            // Fenced code blocks, closed or still being typed, then inline code.
            Regex("```[\\s\\S]*?(?:```|$)"),
            Regex("`[^`\\n]+`"),
            // nostr: references and bare bech32 entities.
            Regex(
                "(?:nostr:)?(?:npub|nsec|note|nevent|nprofile|naddr|nrelay|nembed|ncryptsec)1[02-9ac-hj-np-z]+",
                RegexOption.IGNORE_CASE,
            ),
            // Lightning invoices, LNURLs and Cashu tokens.
            Regex("(?:lightning:)?(?:lnbc|lntb|lnbcrt|lnurl)[0-9a-z]+", RegexOption.IGNORE_CASE),
            Regex("cashu[AB][A-Za-z0-9_\\-=]+"),
            Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+"),
            // Hashtags, @mentions and :shortcode: emoji. Letters are matched broadly so a hashtag
            // in any script is skipped whole.
            Regex("#[^\\s#.,;:!?()\\[\\]{}\"']+"),
            Regex("@[^\\s@,;:!?()\\[\\]{}\"']+"),
            Regex(":[A-Za-z0-9_+\\-]+:"),
        )

    fun words(text: String): List<WordRange> {
        if (text.isEmpty()) return emptyList()

        val skipped = BooleanArray(text.length)
        skipUrls(text, skipped)
        skipPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                for (i in match.range) skipped[i] = true
            }
        }

        val result = ArrayList<WordRange>()
        var i = 0
        while (i < text.length) {
            if (!text[i].isLetter()) {
                i++
                continue
            }

            val start = i
            var end = i
            var hasDigit = false
            var hasLower = false
            var hasUpper = false
            var camel = false
            var touchesSkipped = false
            var j = i
            while (j < text.length) {
                val c = text[j]
                when {
                    c.isLetter() -> {
                        if (c.isUpperCase()) {
                            if (hasLower) camel = true
                            hasUpper = true
                        } else if (c.isLowerCase()) {
                            hasLower = true
                        }
                    }
                    c.isDigit() -> hasDigit = true
                    c.category == CharCategory.NON_SPACING_MARK -> {}
                    // An apostrophe only belongs to the word when a letter follows: "don't" is one
                    // word, the closing quote in 'like this' is not part of "this".
                    isApostrophe(c) && j + 1 < text.length && text[j + 1].isLetter() -> {}
                    else -> break
                }
                if (skipped[j]) touchesSkipped = true
                j++
                end = j
            }
            i = end

            if (touchesSkipped) continue
            if (hasDigit || camel) continue
            if (end - start < 2) continue
            if (hasUpper && !hasLower) continue
            // Identifiers: snake_case, file.ext, and words glued to a link or a code span.
            val before = if (start > 0) text[start - 1] else ' '
            val after = if (end < text.length) text[end] else ' '
            if (before == '_' || after == '_') continue
            // "2nd", "4k": the scan starts at the first letter, after the digits.
            if (before.isDigit()) continue
            if (after == '.' && end + 1 < text.length && text[end + 1].isLetter()) continue
            if (before == '.' && start >= 2 && text[start - 2].isLetter()) continue
            if ((start > 0 && skipped[start - 1]) || (end < text.length && skipped[end])) continue

            result.add(WordRange(start, end))
        }
        return result
    }

    private fun isApostrophe(c: Char) = c == '\'' || c == '’'

    private fun skipUrls(
        text: String,
        skipped: BooleanArray,
    ) {
        var searchFrom = 0
        UrlDetector(text).detect().forEach { url ->
            val start = text.indexOf(url.originalUrl, searchFrom)
            if (start >= 0) {
                val end = start + url.originalUrl.length
                for (k in start until end) skipped[k] = true
                searchFrom = end
            }
        }
    }
}
