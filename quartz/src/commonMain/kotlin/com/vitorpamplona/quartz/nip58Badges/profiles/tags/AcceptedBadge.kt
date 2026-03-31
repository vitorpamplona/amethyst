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
package com.vitorpamplona.quartz.nip58Badges.profiles.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag

/**
 * Represents a paired `a` (badge definition) and `e` (badge award) tag
 * as specified in NIP-58 Profile Badges.
 *
 * Clients SHOULD ignore unpaired `a` or `e` tags.
 */
@Stable
class AcceptedBadge(
    val badgeDefinition: ATag,
    val badgeAward: ETag,
) {
    companion object {
        /**
         * Parses ordered consecutive pairs of `a` and `e` tags from a tag array.
         * Tags must appear as sequential pairs: `a`, `e`, `a`, `e`, ...
         * Unpaired tags are ignored per the spec.
         */
        fun parseAll(tags: TagArray): List<AcceptedBadge> {
            val result = mutableListOf<AcceptedBadge>()
            var i = 0
            while (i < tags.size - 1) {
                val aTag = ATag.parse(tags[i])
                if (aTag != null) {
                    val eTag = ETag.parse(tags[i + 1])
                    if (eTag != null) {
                        result.add(AcceptedBadge(aTag, eTag))
                        i += 2
                        continue
                    }
                }
                i++
            }
            return result
        }

        fun assemble(badges: List<AcceptedBadge>): List<Array<String>> =
            badges.flatMap { badge ->
                listOf(
                    badge.badgeDefinition.toATagArray(),
                    badge.badgeAward.toTagArray(),
                )
            }
    }
}
