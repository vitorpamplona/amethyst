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
 * Walks every `#[n]` reference in [content], handing each callback the digits between the brackets.
 *
 * Jumps between `#` occurrences with `indexOf` — an intrinsified char search — then matches
 * `#[<digits>]` directly rather than anchoring a regex there.
 *
 * **Why not a regex.** On Android `java.util.regex` is ICU-backed, and `Matcher.region()` ->
 * `reset()` -> `MatcherNative.setInput()` copies the *entire input* into native memory per call,
 * so anchoring a fresh Matcher at each candidate cost a full native UTF-16 copy of the content per
 * `#`. See [findHashtags] for the measurements; this scanner shares the defect but never fires on
 * real data, since `#[0]` is the legacy citation form that no current client emits.
 *
 * [tagSearch] is kept as the specification the scan is tested against, not used here.
 */
private inline fun forEachIndexTag(
    content: String,
    action: (digits: String) -> Unit,
) {
    var h = content.indexOf('#')
    while (h >= 0) {
        // `(?:\s|\A)` — the `#` must open the string or follow one ASCII space character.
        if (h == 0 || isAsciiRegexSpace(content[h - 1])) {
            if (h + 1 < content.length && content[h + 1] == '[') {
                var d = h + 2
                while (d < content.length && content[d] in '0'..'9') d++
                // `([0-9]+)` needs a digit, and the `]` must actually be there.
                if (d > h + 2 && d < content.length && content[d] == ']') {
                    action(content.substring(h + 2, d))
                    h = content.indexOf('#', d + 1)
                    continue
                }
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
    forEachIndexTag(content) { digits ->
        try {
            // Out-of-range indexes and non-numeric digits land in the catch below.
            val tag = tags[digits.toInt()]
            if (tag.size > 1 && tag[0] == "p") {
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
    forEachIndexTag(content) { digits ->
        try {
            // Out-of-range indexes and non-numeric digits land in the catch below.
            val tag = tags[digits.toInt()]
            if (tag.size > 1 && tag[0] == "e") {
                output.add(tag[1])
            }
            if (tag.size > 1 && tag[0] == "a") {
                output.add(tag[1])
            }
        } catch (e: Exception) {
        }
    }
    return output
}
