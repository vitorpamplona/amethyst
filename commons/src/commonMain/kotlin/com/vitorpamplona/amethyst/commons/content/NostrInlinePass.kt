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
package com.vitorpamplona.amethyst.commons.content

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.TagContext
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment

/**
 * Finds what Nostr text carries in a run of plain text: `nostr:` references, hashtags, custom
 * emoji, URLs and media, invoices, Cashu tokens, math. It is [RichTextParser], the parser notes use,
 * run over the text a markup format leaves as plain text, so every format recognizes the same
 * things a note does.
 *
 * [tags] are the event's: custom emoji and `imeta` media come from them. [callbackUri] and
 * [authorPubKey] are what a note's parse gets (the post a media item opens, and whose servers can
 * find a lost blob).
 */
class NostrInlinePass(
    private val tags: ImmutableListOfLists<String>,
    private val callbackUri: String? = null,
    private val authorPubKey: String? = null,
) {
    private val parser = RichTextParser()

    /** The `imeta` and emoji maps, built on the first run of text and shared by every run after. */
    private val context by lazy { TagContext(tags) }

    /** [text] as inlines. Spaces at either end are kept; a run of spaces inside becomes one. */
    fun inlines(text: String): List<Inline> {
        if (text.isEmpty()) return emptyList()
        if (text.isBlank()) return listOf(Text(" "))

        val leading = text.first().isWhitespace()
        val trailing = text.last().isWhitespace()

        val out = ArrayList<Inline>()
        val pending = StringBuilder()

        fun flush() {
            if (pending.isNotEmpty()) {
                out.add(Text(pending.toString()))
                pending.setLength(0)
            }
        }

        if (leading) pending.append(' ')

        // Words, in order, across the parser's lines: a run holds no line breaks of its own (they are
        // inlines), and the empty words around a run's edge spaces carry nothing.
        var first = true
        // Markup collapses a run of spaces to one, and the parser keeps whatever spaces it is given.
        val words = WHITESPACE.replace(text.trim(), " ")
        parser.parseText(words, context, callbackUri, authorPubKey).paragraphs.forEach { paragraph ->
            paragraph.words.forEach { word ->
                if (word.segmentText.isNotEmpty()) {
                    if (!first) pending.append(' ')
                    first = false
                    if (word is RegularTextSegment || word.isGuessedMedia(words)) {
                        pending.append(word.segmentText.removePrefix(GUESSED_SCHEME))
                    } else {
                        flush()
                        out.add(NostrInline(word))
                    }
                }
            }
        }

        if (trailing) pending.append(' ')
        flush()
        return out
    }

    /**
     * The parser reads a scheme-less word that ends like a media file (`Scarlet_Witch.jpg`) as a URL
     * and adds `https://`. Prose names files often, and a guessed host is a broken image, so media
     * counts only when its URL is in the text as written.
     */
    private fun Segment.isGuessedMedia(text: String): Boolean = (this is ImageSegment || this is VideoSegment) && segmentText.startsWith(GUESSED_SCHEME) && !text.contains(segmentText)

    companion object {
        private const val GUESSED_SCHEME = "https://"
        private val WHITESPACE = Regex("[ \\t\\r\\n]+")

        /** No event around the text: no custom emoji, no `imeta`. */
        val NONE = NostrInlinePass(EmptyTagList)
    }
}
