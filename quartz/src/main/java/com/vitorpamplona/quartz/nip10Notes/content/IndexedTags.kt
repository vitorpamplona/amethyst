/**
 * Copyright (c) 2024 Vitor Pamplona
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
import java.util.regex.Pattern

val tagSearch = Pattern.compile("(?:\\s|\\A)\\#\\[([0-9]+)\\]")

/**
 * Returns the old-style [1] tag that pionts to an index in the tag array
 */
fun findIndexTagsWithPeople(
    content: String,
    tags: TagArray,
    output: MutableSet<String> = mutableSetOf<String>(),
): List<String> {
    val matcher = tagSearch.matcher(content)
    while (matcher.find()) {
        try {
            val tag = matcher.group(1)?.let { tags[it.toInt()] }
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
    val matcher = tagSearch.matcher(content)
    while (matcher.find()) {
        try {
            val tag = matcher.group(1)?.let { tags[it.toInt()] }
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
