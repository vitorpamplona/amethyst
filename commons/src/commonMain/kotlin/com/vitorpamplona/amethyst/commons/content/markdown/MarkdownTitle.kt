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
package com.vitorpamplona.amethyst.commons.content.markdown

/**
 * Articles often open with their title as a heading (`# Title`), and a screen that shows the title
 * tag above the body would show it twice. This drops that first line when it repeats [title].
 */
object MarkdownTitle {
    fun withoutLeadingTitle(
        markdown: String,
        title: String?,
    ): String {
        if (title.isNullOrBlank()) return markdown
        val start = markdown.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return markdown
        val end = markdown.indexOf('\n', start).let { if (it < 0) markdown.length else it }
        val match = ATX_HEADING.matchEntire(markdown.substring(start, end).trimEnd()) ?: return markdown
        return if (words(match.groupValues[1]) == words(title)) markdown.substring(end).trimStart('\r', '\n') else markdown
    }

    private fun words(text: String) =
        text
            .replace(EMPHASIS, "")
            .split(SPACES)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .lowercase()

    private val ATX_HEADING = Regex("#{1,2}[ \\t]+(.*?)(?:[ \\t]+#+)?")
    private val EMPHASIS = Regex("[*_`]")
    private val SPACES = Regex("\\s+")
}
