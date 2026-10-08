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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.tags

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `z`: which document a P2P event is. NIP-69 orders say `order`. Mostro's split kinds keep the
 * tag (mostro_separate_kinds.md, Phase 4) with `rating` (38384), `info` (38385), `dispute`
 * (38386) and `dev-fee-payment` (8383).
 */
class DocumentTypeTag {
    companion object {
        const val TAG_NAME = "z"
        const val ORDER = "order"
        const val RATING = "rating"
        const val INFO = "info"
        const val DISPUTE = "dispute"
        const val DEV_FEE_PAYMENT = "dev-fee-payment"

        /** True when [tags] carry a `z` naming [type]. Allocation-free, for the factory's tag split. */
        fun isType(
            tags: TagArray,
            type: String,
        ) = tags.fastAny { it.size > 1 && it[0] == TAG_NAME && it[1] == type }

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(type: String = ORDER) = arrayOf(TAG_NAME, type)
    }
}
