/**
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
package com.vitorpamplona.quartz.experimental.zapPolls

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.zapPolls.tags.ClosedAtTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.ConsensusThresholdTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.MaximumTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.MinimumTag
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class PollNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun pollOptionsArray() = tags.mapNotNull(PollOptionTag::parse)

    fun pollOptions() = pollOptionsArray().associate { it.index to it.descriptor }

    fun minAmount() = tags.firstNotNullOfOrNull(MinimumTag::parse)

    fun maxAmount() = tags.firstNotNullOfOrNull(MaximumTag::parse)

    fun closedAt() = tags.firstNotNullOfOrNull(ClosedAtTag::parse)

    fun consensusThreshold() = tags.firstNotNullOfOrNull(ConsensusThresholdTag::parse)

    companion object {
        const val KIND = 6969
        const val ALT_DESCRIPTION = "Poll event"

        fun build(
            post: String,
            options: List<PollOptionTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PollNoteEvent>.() -> Unit = {},
        ): EventTemplate<PollNoteEvent> {
            val tags = TagArrayBuilder<PollNoteEvent>()
            tags.pollOptions(options)
            tags.alt(ALT_DESCRIPTION)
            tags.apply(initializer)
            return EventTemplate(createdAt, KIND, tags.build(), post)
        }
    }
}
