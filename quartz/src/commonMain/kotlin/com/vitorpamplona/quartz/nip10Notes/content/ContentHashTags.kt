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
package com.vitorpamplona.quartz.nip10Notes.content

val hashtagSearch = Regex("(?:\\s|\\A)#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)")

/**
 * Characters that end a hashtag: the punctuation class spelled out in [hashtagSearch], plus ASCII
 * whitespace. Everything else continues the tag — including every non-ASCII character, since the
 * regex's class is ASCII-only, so accented letters, CJK and emoji are all valid tag content.
 */
private val HASHTAG_TERMINATORS =
    BooleanArray(128).apply {
        for (c in 0x09..0x0D) this[c] = true
        this[' '.code] = true
        for (c in "!@#\u0024%^&*()=+./,[{]};:'\"?><") this[c.code] = true
    }

/**
 * True while [c] can still be part of a hashtag.
 *
 * Non-ASCII always continues the tag: [hashtagSearch]'s excluded set is entirely ASCII and its
 * `\s` is ASCII-only, so nothing above 0x7F was ever excluded.
 */
private fun isHashtagChar(c: Char): Boolean = c.code >= 128 || !HASHTAG_TERMINATORS[c.code]

/**
 * Collects the hashtags in [content].
 *
 * Jumps between `#` occurrences with `indexOf` — an intrinsified char search — and then matches
 * `#<tag>` directly, character by character, rather than anchoring a regex there.
 *
 * **Why not a regex.** On Android `java.util.regex` is ICU-backed, and `Matcher.region()` ->
 * `reset()` -> `MatcherNative.setInput()` copies the *entire input* into native memory on every
 * call. `Regex.matchAt` builds a fresh Matcher per call, so anchoring one at each candidate cost a
 * full native UTF-16 copy of the note's content **per `#`** — the same defect that drove the app's
 * native heap to ~1.9GB on a cold start via the NIP-19 scanner. This one is worse: measured over
 * 2588 real notes it minted 43,626 Matchers copying 9.6GB in total, with a single 119KB note
 * costing 279MB, because a whitespace-preceded `#` is far more common in prose than a NIP-19
 * prefix. The Java `Matcher` object is tiny, so Java-heap-driven GC had no reason to reclaim them
 * promptly while each pinned native memory.
 *
 * [hashtagSearch] is kept as the specification the scan is tested against, not used here.
 */
fun findHashtags(
    content: String,
    output: MutableSet<String> = mutableSetOf(),
): List<String> {
    if (content.isBlank()) return emptyList()

    var h = content.indexOf('#')
    while (h >= 0) {
        // `(?:\s|\A)` — the `#` must open the string or follow one ASCII space character.
        if (h == 0 || isAsciiRegexSpace(content[h - 1])) {
            var end = h + 1
            while (end < content.length && isHashtagChar(content[end])) end++
            // The tag group is `+`, so it needs at least one character.
            if (end > h + 1) {
                val tag = content.substring(h + 1, end)
                // Non-ASCII whitespace (U+00A0 and friends) is valid tag content to the regex but
                // still blank to Kotlin, and the old code dropped those too.
                if (tag.isNotBlank()) output.add(tag)
                h = content.indexOf('#', end)
                continue
            }
        }
        h = content.indexOf('#', h + 1)
    }
    return output.toList()
}
