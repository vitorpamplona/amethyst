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
package com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * PROVISIONAL presentation hints on a list header, as Brainstorm writes them
 * (`lib/displayHints.ts`, 2026-10): which declared field is an item's title, its summary,
 * its picture, its link, its playable media or its location, plus any number of `fact` fields an
 * item's page lists as "label: value".
 *
 * `["display", <role>, <declared field>]` / `["display", "fact", <declared field>, <label>?]`
 *
 * No spec defines these yet; they are read so a list looks the same in both clients. A hint
 * decorates a declared field and never invents one, and the first hint per role wins.
 */
@Immutable
data class DisplayHint(
    val role: String,
    val field: String,
    val label: String? = null,
)

class DisplayHintTag {
    companion object {
        const val TAG_NAME = "display"

        const val TITLE = "title"
        const val SUMMARY = "summary"
        const val IMAGE = "image"
        const val LINK = "link"
        const val MEDIA = "media"
        const val LOCATION = "location"
        const val FACT = "fact"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[2].isNotEmpty()

        fun parse(tag: Array<String>): DisplayHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            return DisplayHint(tag[1], tag[2], tag.getOrNull(3)?.trim()?.ifEmpty { null })
        }

        fun assemble(
            role: String,
            field: String,
            label: String? = null,
        ) = arrayOfNotNull(TAG_NAME, role, field, label)
    }
}
