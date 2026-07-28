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
package com.vitorpamplona.amethyst.commons.model.highlights

/**
 * What to render for a NIP-84 highlight: the passage to show, and the range inside it that
 * the user actually marked.
 *
 * When the event carries a `context` tag the whole surrounding sentence is shown with the
 * quote marked inside it. When it doesn't — or the quote can't be located in the context —
 * [text] is the quote alone and [marked] is null, meaning "mark all of it".
 */
data class HighlightQuote(
    val text: String,
    val marked: IntRange?,
) {
    companion object {
        /**
         * @param highlight the `content` of the highlight event — the marked passage.
         * @param context the optional surrounding text the highlight was taken from.
         * @param prefix the W3C TextQuoteSelector prefix, used to pick the right occurrence
         *   when the quote appears more than once in the context.
         */
        fun of(
            highlight: String,
            context: String?,
            prefix: String? = null,
        ): HighlightQuote {
            if (highlight.isEmpty()) return HighlightQuote(context.orEmpty(), null)
            if (context.isNullOrBlank()) return HighlightQuote(highlight, null)

            val at = locate(context, highlight, prefix)
            return if (at != null) {
                HighlightQuote(context, at until (at + highlight.length))
            } else {
                // Context that doesn't actually contain the quote is worse than no context:
                // it would mark nothing and silently show text the user never highlighted.
                HighlightQuote(highlight, null)
            }
        }

        /**
         * Finds [highlight] inside [context], preferring the occurrence whose preceding text
         * ends with [prefix]. Highlighters emit that prefix precisely so a repeated quote can
         * be pinned to the right spot.
         */
        private fun locate(
            context: String,
            highlight: String,
            prefix: String?,
        ): Int? {
            val occurrences = occurrencesOf(context, highlight)
            if (occurrences.isEmpty()) return null
            if (occurrences.size == 1 || prefix.isNullOrBlank()) return occurrences.first()

            val tail = prefix.trimEnd()
            return occurrences.firstOrNull { context.substring(0, it).trimEnd().endsWith(tail) }
                ?: occurrences.first()
        }

        private fun occurrencesOf(
            context: String,
            highlight: String,
        ): List<Int> {
            val found = mutableListOf<Int>()
            var from = context.indexOf(highlight)
            while (from >= 0) {
                found.add(from)
                from = context.indexOf(highlight, from + 1)
            }
            return found
        }
    }
}
