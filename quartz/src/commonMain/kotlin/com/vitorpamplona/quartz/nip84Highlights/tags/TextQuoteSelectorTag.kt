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
package com.vitorpamplona.quartz.nip84Highlights.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * W3C Web Annotation TextQuoteSelector, as emitted by web-based highlighter clients
 * alongside NIP-84 kind:9802 highlights. It anchors the highlight inside the source
 * page ([HighlightEvent.inUrl]) by the exact text and the text immediately before
 * (prefix) and after (suffix) it.
 *
 * The tag is laid out as `["textquoteselector", exact, prefix, suffix]`. The exact
 * field is often a placeholder ("-" or empty) because the highlighted text is already
 * carried by the event's `.content`; [exact] is null in that case.
 */
@Immutable
class TextQuoteSelectorTag(
    val exact: String?,
    val prefix: String?,
    val suffix: String?,
) {
    companion object {
        const val TAG_NAME = "textquoteselector"

        private fun field(value: String?): String? = value?.takeIf { it.isNotEmpty() && it != "-" }

        fun parse(tag: Array<String>): TextQuoteSelectorTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            val exact = field(tag.getOrNull(1))
            val prefix = field(tag.getOrNull(2))
            val suffix = field(tag.getOrNull(3))

            ensure(exact != null || prefix != null || suffix != null) { return null }

            return TextQuoteSelectorTag(exact, prefix, suffix)
        }

        fun assemble(
            exact: String?,
            prefix: String?,
            suffix: String?,
        ) = arrayOf(TAG_NAME, exact ?: "-", prefix ?: "", suffix ?: "")
    }
}
