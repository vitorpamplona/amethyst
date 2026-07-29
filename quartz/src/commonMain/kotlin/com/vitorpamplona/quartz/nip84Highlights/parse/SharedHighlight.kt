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
 * The structured result of parsing a browser share into the pieces a NIP-84 kind:9802
 * highlight needs. Produced by [SharedHighlightParser]; consumed by
 * [com.vitorpamplona.quartz.nip84Highlights.HighlightEvent.Companion.create] and by the
 * highlight composer UI (which pre-fills its fields and lets the user confirm/edit before
 * signing).
 *
 * @property quote the highlighted passage → the event `content`
 * @property url the cleaned source URL (trackers and text-fragment stripped) → an `r` tag
 * @property prefix text just before the highlight, for a `textquoteselector` anchor
 * @property suffix text just after the highlight, for a `textquoteselector` anchor
 */
class SharedHighlight(
    val quote: String?,
    val url: String?,
    val prefix: String?,
    val suffix: String?,
) {
    /** True when nothing usable was found (neither a passage nor a source URL). */
    fun isEmpty(): Boolean = quote.isNullOrBlank() && url.isNullOrBlank()

    /** True when there is at least a highlighted passage or a source URL. */
    fun isNotEmpty(): Boolean = !isEmpty()

    fun hasSelector(): Boolean = !prefix.isNullOrEmpty() || !suffix.isNullOrEmpty()
}
