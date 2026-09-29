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
package com.vitorpamplona.quartz.nip85TrustedAssertions.tags

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag

/**
 * The scores a NIP-85 event, address or external-id assertion (30383-30385) states about its
 * subject, keyed by their NIP-85 tag names (`rank`, `comment_cnt`, `zap_amount`, …): the props of
 * its `SUBJECT` link. The first of each wins, as the classes' accessors read them. Null when the
 * assertion states none.
 */
internal fun TagArray.contentAssertionScores(): Map<String, Any>? {
    val scores = LinkedHashMap<String, Any>()
    fastForEach { tag ->
        if (tag.size < 2 || tag[0] in scores) return@fastForEach
        val value: Number? =
            when (tag[0]) {
                RankTag.TAG_NAME -> RankTag.parse(tag)
                CommentCountTag.TAG_NAME -> CommentCountTag.parse(tag)
                QuoteCountTag.TAG_NAME -> QuoteCountTag.parse(tag)
                RepostCountTag.TAG_NAME -> RepostCountTag.parse(tag)
                ReactionCountTag.TAG_NAME -> ReactionCountTag.parse(tag)
                ZapCountTag.TAG_NAME -> ZapCountTag.parse(tag)
                ZapAmountTag.TAG_NAME -> ZapAmountTag.parse(tag)
                else -> null
            }
        if (value != null) scores[tag[0]] = value
    }
    return scores.ifEmpty { null }
}
