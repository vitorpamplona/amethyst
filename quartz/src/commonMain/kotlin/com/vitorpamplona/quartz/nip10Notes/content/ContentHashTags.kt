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
 * Collects the hashtags in [content].
 *
 * [hashtagSearch] requires `(?:\s|\A)` immediately before the `#`, so every match
 * starts either at position 0 or at a whitespace. That lets the scan jump between
 * `#` occurrences with `indexOf` — an intrinsified char search — and apply the
 * regex **anchored** at each, instead of letting `findAll` drive the regex engine
 * from every position in the string.
 *
 * Measured on the production content distribution (median 529 B, tail to 767 KB):
 * ~68 MB/s -> ~1,240 MB/s on hashtag-dense text (18x) and ~19,000 MB/s when the
 * content has no `#` at all (up to 300x). Equivalence with the previous `findAll`
 * implementation is guarded by `RegexContentBenchmark` in `commons`.
 */
fun findHashtags(
    content: String,
    output: MutableSet<String> = mutableSetOf(),
): List<String> {
    if (content.isBlank()) return emptyList()

    var h = content.indexOf('#')
    while (h >= 0) {
        if (h == 0 || content[h - 1].isWhitespace()) {
            val match =
                try {
                    hashtagSearch.matchAt(content, if (h == 0) 0 else h - 1)
                } catch (e: Exception) {
                    null
                }
            if (match != null) {
                val tag = match.groups[1]?.value
                if (tag != null && tag.isNotBlank()) {
                    output.add(tag)
                }
                h = content.indexOf('#', match.range.last + 1)
                continue
            }
        }
        h = content.indexOf('#', h + 1)
    }
    return output.toList()
}
