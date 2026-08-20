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
package com.vitorpamplona.quartz.experimental.trustedLists.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * The tag definition the membership was computed from:
 * `["source-tag", <eventId>, <author>, <slug>]`.
 */
@Immutable
data class SourceTag(
    val eventId: HexKey,
    val author: HexKey? = null,
    val slug: String? = null,
) {
    fun toTagArray() = assemble(eventId, author, slug)

    companion object {
        const val TAG_NAME = "source-tag"

        fun isTag(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].length == 64

        fun parse(tag: Tag): SourceTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return SourceTag(
                tag[1],
                tag.getOrNull(2)?.ifEmpty { null },
                tag.getOrNull(3)?.ifEmpty { null },
            )
        }

        fun assemble(
            eventId: HexKey,
            author: HexKey?,
            slug: String?,
        ) = arrayOfNotNull(TAG_NAME, eventId, author, slug)
    }
}
