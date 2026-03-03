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
package com.vitorpamplona.quartz.nip88Polls.poll

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class PollEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    fun options() = tags.options()

    fun relays() = tags.relays()

    fun pollType() = tags.pollType()

    fun endsAt() = tags.endsAt()

    fun hasEnded() = tags.hasEnded()

    companion object {
        const val KIND = 1068
        const val ALT_DESCRIPTION = "Poll"

        fun build(
            description: String,
            options: List<OptionTag>,
            endsAt: Long?,
            relays: List<NormalizedRelayUrl>,
            pollType: PollType = PollType.SINGLE_CHOICE,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PollEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            alt(ALT_DESCRIPTION)
            poolType(pollType)
            options(options)
            relays(relays)
            endsAt?.let {
                endsAt(it)
            }
            initializer()
        }
    }
}
