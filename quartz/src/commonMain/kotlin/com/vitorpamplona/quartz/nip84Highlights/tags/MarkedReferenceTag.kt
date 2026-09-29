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
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-84's `r` tag: `["r", <url or text>, <marker>]`. The value names the highlighted source, and
 * NIP-84 lets it be any text, not only a URL. In a quote highlight the [MENTION_MARKER] marks a
 * URL the comment cites instead, and [SOURCE_MARKER] the source itself.
 */
@Immutable
data class MarkedReferenceTag(
    val reference: String,
    val marker: String? = null,
) {
    fun isSource() = marker == SOURCE_MARKER

    fun isMention() = marker == MENTION_MARKER

    fun toTagArray() = assemble(reference, marker)

    companion object {
        const val TAG_NAME = "r"

        const val SOURCE_MARKER = "source"
        const val MENTION_MARKER = "mention"

        fun parse(tag: Array<String>): MarkedReferenceTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return MarkedReferenceTag(tag[1], tag.getOrNull(2)?.ifBlank { null })
        }

        fun assemble(
            reference: String,
            marker: String?,
        ) = arrayOfNotNull(TAG_NAME, reference, marker)
    }
}
