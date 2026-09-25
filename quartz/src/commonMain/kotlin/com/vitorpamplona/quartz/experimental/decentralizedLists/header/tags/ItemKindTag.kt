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
 * Decentralized Lists Cross-NIP Compatibility: `["item-kind", "<kind>", <description>?]` on a
 * list header, one per accepted kind. It widens the items a list accepts beyond 9999/39999 to
 * events of other NIPs (e.g. 34550 NIP-72 communities) that carry `z` tags themselves, or that
 * a curator's 9999/39999 references with an `a` tag.
 *
 * A header without any `item-kind` accepts only 9999/39999. Its `required`/`allowed` rules
 * apply on top of whatever the foreign kind's own NIP requires, never instead of it.
 */
@Immutable
data class ItemKind(
    val kind: Int,
    val description: String? = null,
)

class ItemKindTag {
    companion object {
        const val TAG_NAME = "item-kind"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].toIntOrNull() != null

        fun parse(tag: Array<String>): ItemKind? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val kind = tag[1].toIntOrNull() ?: return null
            ensure(kind >= 0) { return null }
            return ItemKind(kind, tag.getOrNull(2)?.ifEmpty { null })
        }

        fun assemble(
            kind: Int,
            description: String? = null,
        ) = arrayOfNotNull(TAG_NAME, kind.toString(), description)

        fun assemble(itemKind: ItemKind) = assemble(itemKind.kind, itemKind.description)
    }
}
