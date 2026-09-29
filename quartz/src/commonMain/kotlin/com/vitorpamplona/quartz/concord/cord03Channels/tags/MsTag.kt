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
package com.vitorpamplona.quartz.concord.cord03Channels.tags

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The `["ms", "<0..999>"]` sub-second remainder every Concord Chat rumor carries (CORD-02 §4):
 * `created_at` stays whole unix seconds, untweaked (CORD-01), and the true send time is
 * `created_at * 1000 + ms`. Every comparison the protocol makes (message order, edit recency)
 * uses that basis.
 *
 * Parsing is strict decimal, like the reference client's `resolveMs`: `"0"` or `"1"`…`"999"`
 * with no leading zero, sign, whitespace or exponent. A tag outside that shape is malformed, and
 * a malformed rumor is dropped rather than interpreted (CORD-02 §5), so the excess can never
 * smuggle ordering the author's clock did not produce.
 */
class MsTag {
    companion object {
        const val TAG_NAME = "ms"

        private val CANONICAL = Regex("^(0|[1-9][0-9]{0,2})$")

        /** The remainder in [tag], or null when it is not an `ms` tag or is malformed. */
        fun parse(tag: Array<String>): Int? {
            if (tag.isEmpty() || tag[0] != TAG_NAME) return null
            val raw = tag.getOrNull(1) ?: return null
            if (!CANONICAL.matches(raw)) return null
            return raw.toInt()
        }

        fun assemble(ms: Int): Array<String> {
            require(ms in 0..999) { "ms remainder must be in 0..999, was $ms" }
            return arrayOf(TAG_NAME, ms.toString())
        }

        /**
         * The sub-second remainder of "now" when [createdAt] is the current second, else 0. A
         * builder handed the current `TimeUtils.now()` thus stamps the real millisecond; one
         * handed any other second (a fixed test time, a backdated rumor, a rollover between the
         * two clock reads) stamps 0, which is still a well-formed, ordering-safe tag.
         */
        fun remainderFor(createdAt: Long): Int {
            val nowMs = TimeUtils.nowMillis()
            return if (nowMs / 1000 == createdAt) (nowMs % 1000).toInt() else 0
        }

        /**
         * The rumor's ordering basis `createdAt * 1000 + ms` (CORD-02 §4). A missing tag counts
         * as 0; a malformed or duplicated one yields null, and the caller drops the rumor.
         */
        fun orderingMs(
            createdAt: Long,
            tags: TagArray,
        ): Long? {
            var found: Int? = null
            var count = 0
            for (tag in tags) {
                if (tag.isEmpty() || tag[0] != TAG_NAME) continue
                count++
                found = parse(tag) ?: return null
            }
            if (count > 1) return null
            return createdAt * 1000 + (found ?: 0)
        }
    }
}
