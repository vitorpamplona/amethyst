/**
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
package com.vitorpamplona.amethyst.service

import android.util.LruCache
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.quartz.nip01Core.core.ImmutableListOfLists

object CachedRichTextParser {
    private val richTextCache = LruCache<Int, RichTextViewerState>(50)

    private fun hashCodeCache(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
    ): Int {
        var result = content.hashCode()
        result = 31 * result + tags.lists.hashCode()
        if (callbackUri != null) {
            result = 31 * result + callbackUri.hashCode()
        }
        return result
    }

    fun cachedText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
    ): RichTextViewerState? = richTextCache[hashCodeCache(content, tags, callbackUri)]

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
    ): RichTextViewerState {
        val key = hashCodeCache(content, tags, callbackUri)
        val cached = richTextCache[key]
        return if (cached != null) {
            cached
        } else {
            val newUrls = RichTextParser().parseText(content, tags, callbackUri)
            richTextCache.put(key, newUrls)
            newUrls
        }
    }
}

object CachedUrlParser {
    private val parsedUrlsCache = LruCache<Int, List<String>>(10)

    fun cachedParseValidUrls(content: String): List<String> = parsedUrlsCache[content.hashCode()]

    fun parseValidUrls(content: String): List<String> {
        if (content.isEmpty()) return emptyList()

        val key = content.hashCode()
        val cached = parsedUrlsCache[key]
        return if (cached != null) {
            cached
        } else {
            val newUrls = RichTextParser().parseValidUrls(content).toList()
            parsedUrlsCache.put(key, newUrls)
            newUrls
        }
    }
}
