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
    // Global across every feed. Sized to hold the active feed's visible + prefetched
    // (see PrefetchFeedMedia) working set plus a few other feeds' recent entries, so
    // pre-parsed bodies survive until the render reads them and feed switches don't
    // thrash. Each entry is one note's parsed segments — typically single-digit KB.
    private val richTextCache = LruCache<Int, RichTextViewerState>(500)
    private val isMarkdownCache = LruCache<Int, Boolean>(200)

    private fun hashCodeCache(
        content: String,
        tags: ImmutableListOfLists<String>,
        callbackUri: String?,
        authorPubKey: String?,
    ): Int {
        var result = content.hashCode()
        // Content-addressed (memoized contentHash), not identity — `lists` is an
        // Array<Array<String>>, so a fresh instance with the same tag content must
        // map to the same entry. This lets an off-thread pre-parse (the feed media
        // prefetcher) populate the exact entry the renderer later looks up, turning
        // the scroll-time parse into a cache hit. The parse is a pure function of
        // tag content, so equal content sharing one entry is correct. The hash is
        // cached on the instance so this stays O(1)-amortized per tag list.
        result = 31 * result + tags.contentHash()
        if (callbackUri != null) {
            result = 31 * result + callbackUri.hashCode()
        }
        if (authorPubKey != null) {
            result = 31 * result + authorPubKey.hashCode()
        }
        return result
    }

    fun trimToSize(maxItems: Int) {
        richTextCache.trimToSize(maxItems)
        // isMarkdownCache is sized at 40% of richTextCache; preserve the ratio
        isMarkdownCache.trimToSize(maxItems * 2 / 5)
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

                if (c == '*') {
                    // CommonMark allows intraword `*` emphasis
                    // (`foo*bar*baz` → `foo<em>bar</em>baz`), so no
                    // word-boundary carve-out here. We still need a
                    // flanking check though: a `*` followed by
                    // whitespace can't open emphasis, and a closing
                    // `*` can't be preceded by whitespace. Without
                    // these, `5 * 3 = 15` and `5 * 3 * 7` would
                    // false-fire.
                    if (i + 1 < len && content[i + 1] == '*') return true
                    val nextChar = if (i + 1 < len) content[i + 1] else ' '
                    if (!nextChar.isMdSpaceOrNewline()) {
                        var j = i + 1
                        while (j < len && content[j] != '\n') {
                            if (content[j] == '*' && !content[j - 1].isMdSpaceOrNewline()) return true
                            j++
                        }
                    }
                }
                if (c == '_') {
                    // CommonMark §6.2 forbids `_` from opening or
                    // closing emphasis intraword — the rule that
                    // makes `snake_case` and `foo_bar_baz` render
                    // literally. Same rule keeps cashuB/cashuA
                    // base64url payloads from false-firing, since
                    // every `_` inside such a token is surrounded
                    // by word chars.
                    //
                    // Practical heuristic: skip when the char before
                    // this `_` is a word char (letter, digit, or
                    // another `_` — the latter folds runs of `_` so
                    // we only evaluate the run's first position).
                    if (i == 0 || !content[i - 1].isMdWordChar()) {
                        if (i + 1 < len && content[i + 1] == '_') {
                            // `__` (or longer) at a non-word
                            // boundary. Walk to the end of the run
                            // and confirm it's followed by
                            // non-whitespace (left-flanking).
                            var runEnd = i + 1
                            while (runEnd < len && content[runEnd] == '_') runEnd++
                            if (runEnd < len && !content[runEnd].isMdSpaceOrNewline()) return true
                        } else {
                            // Single `_` at a non-word boundary.
                            // Find a matching `_` on the same line
                            // that itself is a valid closer (not
                            // followed by a word char).
                            var j = i + 1
                            while (j < len && content[j] != '\n') {
                                if (content[j] == '_') {
                                    val nextIsWord = j + 1 < len && content[j + 1].isMdWordChar()
                                    if (!nextIsWord) return true
                                }
                                j++
                            }
                        }
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
                // A blank line is still "at line start": skipping '\n'/'\r'
                // here keeps isNewLine true so a heading/blockquote/list
                // marker on the next line (the standard `\n\n#` spacing) is
                // still recognized as line-leading. Without the newline
                // exclusion, the second '\n' of a blank line flipped
                // isNewLine to false and ATX headings after a blank line
                // went undetected.
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
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

    // CommonMark "word" character for the intraword-emphasis rule:
    // ASCII letters, ASCII digits, and `_` itself. Used to fold runs of
    // `_` (so we only evaluate the start of a run) and to detect
    // `snake_case`-style intraword underscores.
    private fun Char.isMdWordChar(): Boolean = this.isLetterOrDigit() || this == '_'

    private fun Char.isMdSpaceOrNewline(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'

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
