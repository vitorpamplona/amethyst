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
package com.vitorpamplona.quartz.nip84Highlights.parse

/**
 * Turns the free-form text a browser hands Amethyst on "Share selection" into the pieces of
 * a NIP-84 highlight. The share intent's plain text can arrive in several shapes and this
 * parser normalises all of them:
 *
 * - **Selection only** — `"Some highlighted sentence."` → quote, no URL.
 * - **Selection + page URL** — `"Some highlighted sentence."\n\nhttps://example.com/post`
 *   (what many browsers and read-it-later apps emit) → quote + source URL.
 * - **URL only** — `https://example.com/post` → source URL, no quote yet.
 * - **Link to highlight** — `https://example.com/post#:~:text=prefix-,Some%20sentence,-after`
 *   (Chrome/Edge/Safari "Copy link to highlight") → the passage decoded from the text
 *   fragment plus its prefix/suffix anchors, with the fragment stripped off the stored URL.
 *
 * The URL is always cleaned of tracking parameters ([UrlTrackerCleaner]) and of its
 * text-fragment directive ([TextFragmentParser]) before being returned. Surrounding quote
 * marks the browser wraps around the selection are trimmed off the passage.
 *
 * The result is a best-effort pre-fill: the composer screen lets the user confirm and edit
 * every field before the event is signed.
 */
object SharedHighlightParser {
    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    // Trailing punctuation that is part of the surrounding sentence, not the URL token.
    // Closing brackets are handled separately (balance-aware) so a Wikipedia URL such as
    // `.../wiki/Mercury_(planet)` keeps its trailing `)`.
    private const val URL_TRAILING_TRIM = ".,;:!?>\"'»”’"

    private val OPEN_TO_CLOSE = mapOf('(' to ')', '[' to ']', '{' to '}')

    // Quote marks a browser may wrap around a shared selection (straight, curly, guillemets).
    private const val QUOTE_CHARS = "\"'“”‘’«»"

    fun parse(shared: String): SharedHighlight {
        val input = shared.trim()
        if (input.isEmpty()) return SharedHighlight(null, null, null, null)

        // The source URL is normally appended after the selection, so prefer the last URL in
        // the string (a URL inside the highlighted text itself stays part of the quote).
        val match = URL_REGEX.findAll(input).lastOrNull()

        var url: String? = null
        var prefix: String? = null
        var suffix: String? = null
        var fragmentQuote: String? = null
        var remainder = input

        if (match != null) {
            val rawToken = match.value
            val rawUrl = trimUrlEnd(rawToken)

            val fragment = TextFragmentParser.parse(rawUrl)
            prefix = fragment?.prefix
            suffix = fragment?.suffix
            fragmentQuote = fragment?.start

            val stripped = TextFragmentParser.stripTextFragment(rawUrl)
            url = UrlTrackerCleaner.clean(stripped).takeIf { it.isNotBlank() }

            // Remove the whole matched token (incl. any trailing punctuation) from the passage.
            remainder = dropDanglingOpenBracket(input.removeRange(match.range).trim())
        }

        val quote = cleanQuote(remainder) ?: fragmentQuote?.let { cleanQuote(it) }

        return SharedHighlight(
            quote = quote,
            url = url,
            prefix = prefix,
            suffix = suffix,
        )
    }

    /**
     * Strips trailing sentence punctuation the URL token accidentally swallowed. A closing
     * bracket is only stripped when it is unbalanced within the token — so a wrapping
     * `(https://example.com)` loses its `)`, but `.../Mercury_(planet)` keeps it.
     */
    private fun trimUrlEnd(token: String): String {
        var end = token.length
        while (end > 0) {
            val c = token[end - 1]
            when {
                c in URL_TRAILING_TRIM -> end--
                c == ')' || c == ']' || c == '}' -> {
                    val open = OPEN_TO_CLOSE.entries.first { it.value == c }.key
                    val opens = token.count { it == open }
                    val closes = token.take(end).count { it == c }
                    if (closes > opens) end-- else return token.substring(0, end)
                }
                else -> return token.substring(0, end)
            }
        }
        return token.substring(0, end)
    }

    /**
     * Drops a bracket left dangling at the end of the passage once the URL token carried its
     * closing partner away. `See this quote (https://example.com/article)` loses the wrapping `)`
     * in [trimUrlEnd], which would otherwise publish the passage as `See this quote (`.
     *
     * Only an *unbalanced* trailing bracket goes, so `He said (see below) https://…` keeps its
     * matched pair, and a bracket anywhere but the very end is left alone — the URL removal can
     * only ever orphan one at the tail.
     */
    private fun dropDanglingOpenBracket(text: String): String {
        var out = text.trimEnd()
        while (out.isNotEmpty()) {
            val open = out.last()
            val close = OPEN_TO_CLOSE[open] ?: return out
            if (out.count { it == open } <= out.count { it == close }) return out
            out = out.dropLast(1).trimEnd()
        }
        return out
    }

    /** Trims surrounding whitespace and matching quote marks; returns null when nothing is left. */
    private fun cleanQuote(text: String): String? {
        val trimmed = text.trim { it.isWhitespace() || it in QUOTE_CHARS }
        return trimmed.ifBlank { null }
    }
}
