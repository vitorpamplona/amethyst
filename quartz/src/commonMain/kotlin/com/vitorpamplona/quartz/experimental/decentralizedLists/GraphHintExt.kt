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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.ConceptGraphTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ElementOfTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.SubsetOfTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritFromTag

/**
 * The coordinate a Tapestry graph tag points at — `b` (inherit-from), `n` (element-of),
 * `s` (subset-of) or `concept-graph` — for the hint providers' `linkedAddressIds()`.
 *
 * None of these tags has a relay slot, so they link without ever producing an address hint.
 * Values that are not `<kind>:<pubkey>:<d>` coordinates (the `b-tag-deferred` marker, a
 * malformed value) are dropped by the same allocation-free shape check the family uses.
 */
internal fun parseGraphCoordinate(tag: Array<String>): String? {
    if (tag.size < 2) return null
    return when (tag[0]) {
        InheritFromTag.TAG_NAME,
        ElementOfTag.TAG_NAME,
        SubsetOfTag.TAG_NAME,
        ConceptGraphTag.TAG_NAME,
        -> tag[1].takeIf { CoordinateShape.matches(it) }

        else -> null
    }
}
