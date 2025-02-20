/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.bounties

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import java.math.BigDecimal

/**
 * This is a huge hack from back in the days...
 */
class BountyAddValueEvent {
    companion object {
        const val KIND = 1
        const val ALT_DESCRIPTION = "Add Value to Bounty"

        const val BOUNTY_HASH_TAG = "bounty-added-reward"

        fun build(
            amount: BigDecimal,
            bountyRoot: EventHintBundle<TextNoteEvent>,
            bountyRootAuthor: PTag,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<TextNoteEvent> {
            val tags = TagArrayBuilder<TextNoteEvent>()
            tags.markedETag(bountyRoot.toMarkedETag(MarkedETag.MARKER.ROOT))
            tags.hashtag(BOUNTY_HASH_TAG)
            tags.pTag(bountyRootAuthor)
            tags.alt(ALT_DESCRIPTION)
            return EventTemplate(createdAt, KIND, tags.build(), amount.toString())
        }
    }
}
