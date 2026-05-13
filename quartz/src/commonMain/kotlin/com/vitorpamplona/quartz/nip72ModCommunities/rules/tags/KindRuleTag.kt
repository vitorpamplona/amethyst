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
package com.vitorpamplona.quartz.nip72ModCommunities.rules.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-9B `k` tag: whitelists one event kind for the community, optionally with a
 * max event size in bytes and a per-author quota per day.
 *
 * Form: `["k", "<kind>", "<max-bytes?>", "<max-per-author-per-day?>"]`
 *
 * Position 2 (kind) is required. Positions 3 and 4 are optional; an empty string
 * is treated as "unset".
 */
@Immutable
data class KindRuleTag(
    val kind: Int,
    val maxBytes: Int?,
    val maxPerAuthorPerDay: Int?,
) {
    fun toTagArray() = assemble(kind, maxBytes, maxPerAuthorPerDay)

    companion object {
        const val TAG_NAME = "k"

        fun parse(tag: Array<String>): KindRuleTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            val kind = tag[1].toIntOrNull() ?: return null
            val maxBytes = tag.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val maxPerDay = tag.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull()

            return KindRuleTag(kind, maxBytes, maxPerDay)
        }

        fun assemble(
            kind: Int,
            maxBytes: Int?,
            maxPerAuthorPerDay: Int?,
        ) = arrayOfNotNull(
            TAG_NAME,
            kind.toString(),
            maxBytes?.toString(),
            maxPerAuthorPerDay?.toString(),
        )
    }
}
