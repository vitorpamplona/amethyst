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
package com.vitorpamplona.amethyst.desktop.service

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

object DesktopCachedRichTextParser {
    private const val MAX_CACHE_SIZE = 50

    // Lock-free get on the feed rich-text render path; the previous access-order
    // synchronizedMap took a monitor even on reads.
    private val cache = ConcurrentLruCache<String, RichTextViewerState>(MAX_CACHE_SIZE)

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
    ): RichTextViewerState {
        cache.get(content)?.let { return it }
        val state = RichTextParser().parseText(content, tags, callbackUri)
        cache.put(content, state)
        return state
    }

    fun isMarkdown(content: String): Boolean =
        content.startsWith("> ") ||
            content.startsWith("# ") ||
            content.contains("##") ||
            content.contains("__") ||
            content.contains("**") ||
            content.contains("```") ||
            content.contains("](")
}
