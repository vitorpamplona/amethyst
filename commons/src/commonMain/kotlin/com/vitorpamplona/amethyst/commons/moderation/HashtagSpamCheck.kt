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
package com.vitorpamplona.amethyst.commons.moderation

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasMoreHashtagsThan
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent

/**
 * Pure decision: should this note be collapsed because it abuses hashtag `t` tags?
 *
 * Reuses [hasMoreHashtagsThan] which short-circuits on total count before
 * counting unique tags, so a note that repeats the same hashtag does not
 * trip the filter.
 *
 * Long-form articles (kind 30023) legitimately use many topic tags and are
 * always exempt. Authors whose pubkey appears in [exemptKeys] (the user's
 * follow list plus self) are also exempt — the caller assembles that set.
 */
object HashtagSpamCheck {
    fun isHashtagSpam(
        displayedEvent: Event?,
        authorPubkey: HexKey?,
        enabled: Boolean,
        threshold: Int,
        exemptKeys: Set<HexKey>,
    ): Boolean {
        if (!enabled) return false
        if (displayedEvent == null) return false
        if (displayedEvent.kind == LongTextNoteEvent.KIND) return false
        if (authorPubkey != null && authorPubkey in exemptKeys) return false
        return displayedEvent.tags.hasMoreHashtagsThan(threshold)
    }
}
