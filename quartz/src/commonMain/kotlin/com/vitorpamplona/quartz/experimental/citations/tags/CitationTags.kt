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
package com.vitorpamplona.quartz.experimental.citations.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The single-value tags a citation may carry. They are plain `[name, value]` pairs, so one
 * parser serves them all rather than a class per tag name.
 */
object CitationTags {
    // Shared by every citation kind.
    const val ACCESSED_ON = "accessed_on"
    const val PUBLISHED_ON = "published_on"
    const val PUBLISHED_BY = "published_by"
    const val AUTHOR = "author"
    const val SUMMARY = "summary"
    const val LOCATION = "location"
    const val VERSION = "version"
    const val GEOHASH = "g"

    // External (kind 31).
    const val URL = "u"
    const val OPEN_TIMESTAMP = "open_timestamp"

    // Hardcopy (kind 32).
    const val PAGE_RANGE = "page_range"
    const val CHAPTER_TITLE = "chapter_title"
    const val EDITOR = "editor"
    const val PUBLISHED_IN = "published_in"
    const val DOI = "doi"

    // Prompt (kind 33).
    const val LLM = "llm"

    fun value(
        tag: Tag,
        name: String,
    ): String? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == name) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        return tag[1]
    }

    /** The second value of a two-value tag — `published_in` carries the volume there. */
    fun secondValue(
        tag: Tag,
        name: String,
    ): String? {
        ensure(tag.has(2)) { return null }
        ensure(tag[0] == name) { return null }
        ensure(tag[2].isNotEmpty()) { return null }
        return tag[2]
    }

    fun assemble(
        name: String,
        value: String,
    ) = arrayOf(name, value)
}
