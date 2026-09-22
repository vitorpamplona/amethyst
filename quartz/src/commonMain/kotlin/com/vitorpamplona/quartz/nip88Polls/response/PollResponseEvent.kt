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
package com.vitorpamplona.quartz.nip88Polls.response

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.tags.PollTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class PollResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    RootScope {
    override fun eventHints() = tags.mapNotNull(PollTag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(PollTag::parseId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    fun responses() = tags.responses()

    fun poll() = tags.poll()

    companion object {
        const val KIND = 1018

        /**
         * The answer to a single-choice poll.
         *
         * NIP-88: "polltype: singlechoice: The first response tag is to be considered the actual
         * response." One code in, one `response` tag out — there is no way to express an answer
         * whose meaning depends on which tag the reader happens to look at first.
         */
        fun buildSingleChoice(
            poll: EventHintBundle<PollEvent>,
            response: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PollResponseEvent>.() -> Unit = {},
        ) = build(poll, listOf(response), createdAt, initializer)

        /**
         * The answer to a multiple-choice poll.
         *
         * NIP-88: "the first response tag pointing to each id is considered the actual response,
         * without considering the order of the response tags" — so a Set is exactly the right
         * shape here, and the conversion to a list below is only about writing them down.
         */
        fun buildMultipleChoice(
            poll: EventHintBundle<PollEvent>,
            responses: Set<String>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PollResponseEvent>.() -> Unit = {},
        ) = build(poll, responses.toList(), createdAt, initializer)

        /**
         * Writes [responses] as `response` tags in the order given.
         *
         * Prefer [buildSingleChoice] / [buildMultipleChoice]: the poll type decides whether order
         * matters, and this overload cannot know which one it is being used for. It takes a List
         * rather than a Set so that a caller who does know keeps control of the order — passing an
         * unordered Set here would hand a single-choice answer to whatever iteration order the
         * collection happened to have.
         */
        fun build(
            poll: EventHintBundle<PollEvent>,
            responses: List<String>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PollResponseEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            poll(poll)
            notifyAuthor(poll)
            responses(responses)
            initializer()
        }
    }
}
