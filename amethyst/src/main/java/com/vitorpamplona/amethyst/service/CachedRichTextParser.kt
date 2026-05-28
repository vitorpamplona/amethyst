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
package com.vitorpamplona.amethyst.service

import android.util.LruCache
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.UrlParser

object CachedRichTextParser {
    private val richTextCache = LruCache<Int, RichTextViewerState>(50)
    private val isMarkdownCache = LruCache<Int, Boolean>(200)

    private fun hashCodeCache(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String?,
    ): Int {
        var result = content.hashCode()
        result = 31 * result + tags.lists.hashCode()
        if (callbackUri != null) {
            result = 31 * result + callbackUri.hashCode()
        }
        if (authorPubKey != null) {
            result = 31 * result + authorPubKey.hashCode()
        }
        return result
    }

    fun cachedText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): RichTextViewerState? = richTextCache[hashCodeCache(content, tags, callbackUri, authorPubKey)]

    fun parseText(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String? = null,
        authorPubKey: String? = null,
    ): RichTextViewerState {
        val key = hashCodeCache(content, tags, callbackUri, authorPubKey)
        val cached = richTextCache[key]
        return if (cached != null) {
            cached
        } else {
            val newUrls = RichTextParser().parseText(content, tags, callbackUri, authorPubKey)
            richTextCache.put(key, newUrls)
            newUrls
        }
    }

    // Shared across every RichTextViewer instance so that the same content quoted in multiple
    // notes only pays for the scan once. The decision is purely a function of `content`.
    fun isMarkdown(content: String): Boolean {
        val key = content.hashCode()
        isMarkdownCache[key]?.let { return it }
        val result = computeIsMarkdown(content)
        isMarkdownCache.put(key, result)
        return result
    }

    private fun computeIsMarkdown(content: String): Boolean {
        // Cashu v2/cashuB tokens are base64url payloads that routinely
        // contain '__' and lone '_' pairs (the base64url alphabet uses
        // '_'; a token whose CBOR ends with a fixed-bytes break easily
        // produces a tail like `______`). The detector below treats
        // those as markdown bold/italic and routes the chat through
        // RenderContentAsMarkdown, which has no CashuSegment support,
        // so the user sees raw base64 instead of the redeem card.
        // cashuA shares the same alphabet and the same risk. Force the
        // rich-text path whenever a cashu token is present.
        if (content.contains("cashuA", true) || content.contains("cashuB", true)) return false

        val len = content.length
        if (len == 0) return false

        var isNewLine = true
        var nonSpaceCharCountOnLine = 0
        var lastNonSpaceChar = ' '
        // True while every non-whitespace char on the current line has
        // been the same '=' or '-' as the first. Required for the
        // setext-heading check below — without it, an ordinary sentence
        // ending in `-` would be promoted to a heading underline.
        var lineIsHomogeneousSetextChar = false

        for (i in 0 until len) {
            val c = content[i]
            val cCode = c.code

            // O(1) trigger-char gate. Allocated once at object init.
            if (cCode < 128 && IS_MARKDOWN_TRIGGER[cCode]) {
                if (c == '`' || c == '|') return true
                if (c == '~' && i + 1 < len && content[i + 1] == '~') return true

                if (isNewLine) {
                    if (c == '#') {
                        var j = i + 1
                        while (j < len && content[j] == '#') j++
                        if (j < len && content[j] == ' ' && (j - i) <= 6) return true
                    }
                    if (c == '>') {
                        if (i + 1 < len && content[i + 1] == ' ') return true
                    }
                    if ((c == '-' || c == '*' || c == '+') && i + 1 < len && content[i + 1] == ' ') return true
                }

                if (c == '*' || c == '_') {
                    if (i + 1 < len && content[i + 1] == c) return true
                    var j = i + 1
                    while (j < len) {
                        if (content[j] == c) return true
                        if (content[j] == '\n') break
                        j++
                    }
                }
                if (c == '[') {
                    var j = i + 1
                    while (j < len && content[j] != ']') {
                        if (content[j] == '\n') break
                        j++
                    }
                    if (j + 1 < len && content[j] == ']' && content[j + 1] == '(') {
                        // A markdown link's URL portion can't span a
                        // newline — bail if we hit '\n' before ')'.
                        var k = j + 2
                        while (k < len && content[k] != ')') {
                            if (content[k] == '\n') break
                            k++
                        }
                        if (k < len && content[k] == ')') return true
                    }
                }
            }

            // Structural line tracking — drives isNewLine for the next
            // iteration and powers the ordered-list + setext-heading
            // checks that need to know "how much non-space text has
            // appeared on the current line".
            if (isNewLine) {
                if (c != ' ' && c != '\t') {
                    isNewLine = false
                    nonSpaceCharCountOnLine = 1
                    lastNonSpaceChar = c
                    lineIsHomogeneousSetextChar = (c == '=' || c == '-')

                    // Ordered list: digit+ followed by `. ` at line start.
                    if (cCode in 48..57) {
                        var j = i + 1
                        while (j < len && content[j].code in 48..57) j++
                        if (j + 1 < len && content[j] == '.' && content[j + 1] == ' ') return true
                    }
                }
            } else {
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    nonSpaceCharCountOnLine++
                    if (c != lastNonSpaceChar) lineIsHomogeneousSetextChar = false
                    lastNonSpaceChar = c
                }

                if (c == '\n' || c == '\r') {
                    // Setext heading: a line of 3+ '=' or '-' (and
                    // nothing else) under non-empty text. Without the
                    // homogeneity check, an ordinary sentence ending
                    // in `-` would be promoted to a heading underline.
                    if (lineIsHomogeneousSetextChar &&
                        nonSpaceCharCountOnLine >= 3 &&
                        (lastNonSpaceChar == '=' || lastNonSpaceChar == '-')
                    ) {
                        return true
                    }
                    isNewLine = true
                    nonSpaceCharCountOnLine = 0
                    lineIsHomogeneousSetextChar = false
                }
            }
        }

        // Trailing-line setext check for content not terminated by '\n'.
        if (lineIsHomogeneousSetextChar &&
            nonSpaceCharCountOnLine >= 3 &&
            (lastNonSpaceChar == '=' || lastNonSpaceChar == '-')
        ) {
            return true
        }

        return false
    }

    // Allocated once at object init; every isMarkdown call does an
    // O(1) lookup against this table instead of branching through ten
    // `contains(...)` calls.
    private val IS_MARKDOWN_TRIGGER =
        BooleanArray(128).apply {
            this['#'.code] = true
            this['*'.code] = true
            this['_'.code] = true
            this['['.code] = true
            this['`'.code] = true
            this['>'.code] = true
            this['-'.code] = true
            this['+'.code] = true
            this['~'.code] = true
            this['|'.code] = true
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
            val urlSet = UrlParser().parseValidUrls(content)
            val newUrls = urlSet.withScheme.filter { it.startsWith("http") } + urlSet.withoutScheme.map { "http://$it" } + urlSet.bech32s.map { "http://$it" }
            parsedUrlsCache.put(key, newUrls)
            newUrls
        }
    }
}
