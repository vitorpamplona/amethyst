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

import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * this is the old way of linking an e tag on the content by [index] the tag
 */

val tagSearch = Regex("(?:\\s|\\A)\\#\\[([0-9]+)\\]")

/**
 * Walks every `#[n]` reference in [content].
 *
 * [tagSearch] requires `(?:\s|\A)` immediately before the `#`, so every match
 * starts at position 0 or at a whitespace. That lets the scan jump between `#`
 * occurrences with `indexOf` — an intrinsified char search — and apply the regex
 * **anchored** at each, instead of letting `findAll` drive the regex engine from
 * every position in the string.
 *
 * Measured on the production content distribution (median 529 B, tail to 767 KB):
 * ~63 MB/s -> multiple GB/s when the content has no `#`, and ~18x on reference-dense
 * text. Equivalence with the previous `findAll` implementation (both callers) is
 * guarded by `RegexContentBenchmark` in `commons`.
 */
private inline fun forEachIndexTag(
    content: String,
    action: (MatchResult) -> Unit,
) {
    var h = content.indexOf('#')
    while (h >= 0) {
        if (h == 0 || content[h - 1].isWhitespace()) {
            val match =
                try {
                    tagSearch.matchAt(content, if (h == 0) 0 else h - 1)
                } catch (e: Exception) {
                    null
                }
            if (match != null) {
                action(match)
                h = content.indexOf('#', match.range.last + 1)
                continue
            }
        }
        h = content.indexOf('#', h + 1)
    }
}

/**
 * Returns the old-style [1] tag that pionts to an index in the tag array
 */
fun findIndexTagsWithPeople(
    content: String,
    tags: TagArray,
    output: MutableSet<String> = mutableSetOf<String>(),
): List<String> {
    forEachIndexTag(content) { index ->
        try {
            val tag = index.groups[1]?.value?.let { tags[it.toInt()] }
            if (tag != null && tag.size > 1 && tag[0] == "p") {
                output.add(tag[1])
            }
        } catch (e: Exception) {
        }
    }

    return output.toList()
}

/**
 * Returns the old-style [1] tag that pionts to an index in the tag array
 */
fun findIndexTagsWithEventsOrAddresses(
    content: String,
    tags: TagArray,
    output: MutableSet<String> = mutableSetOf<String>(),
): Set<String> {
    forEachIndexTag(content) { index ->
        try {
            val tag = index.groups[1]?.value?.let { tags[it.toInt()] }
            if (tag != null && tag.size > 1 && tag[0] == "e") {
                output.add(tag[1])
            }
            if (tag != null && tag.size > 1 && tag[0] == "a") {
                output.add(tag[1])
            }
        } catch (e: Exception) {
        }
    }
    return output
}
