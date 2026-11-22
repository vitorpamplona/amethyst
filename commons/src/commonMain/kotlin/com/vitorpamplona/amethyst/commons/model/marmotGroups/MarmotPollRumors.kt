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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The inner rumors of a Marmot poll (kind 1068) and a vote on one (kind 1018), built the same
 * way by every front end: the app's composer and `amy marmot message poll|vote`. The tag rules
 * are marmot-kmp's ([MarmotPolls]); this adds the mention tags and the rumor around them.
 */
object MarmotPollRumors {
    /**
     * A poll by [author], or null when the input breaks MDK's profile ([MarmotPolls.pollTags]),
     * with the reason in [onError].
     */
    fun poll(
        author: HexKey,
        question: String,
        options: List<String>,
        type: MarmotPolls.PollType,
        endsAt: Long?,
        /** The accounts the question and options mention, `p`-tagged as a message tags them. */
        mentions: List<PTag> = emptyList(),
        createdAt: Long = TimeUtils.now(),
        onError: (MarmotPolls.BuildError) -> Unit = {},
    ): Event? {
        val tags = MarmotPolls.pollTags(createdAt, question, options, type, endsAt, onError) ?: return null
        // MDK ignores tags a poll does not define, within MAX_TAGS in all. A mention past that
        // still reads as a link in the text; it just notifies no one.
        val room = (MarmotPolls.MAX_TAGS - tags.size).coerceAtLeast(0)
        val pTags = mentions.distinctBy { it.pubKey }.take(room).map { it.toTagArray() }
        val template = EventTemplate<Event>(createdAt, MarmotPolls.KIND_POLL, tags + pTags, question)
        return RumorAssembler.assembleRumor(author, template)
    }

    /**
     * A vote by [author] on [poll] choosing [optionIds], or null when the poll is closed or
     * invalid, or the selection does not fit it. A vote is the voter's complete selection,
     * replacing any earlier one. The options go out in the poll's own order, so the same choice
     * is always the same tags; an id the poll does not define makes the selection invalid.
     */
    fun vote(
        author: HexKey,
        poll: Event,
        optionIds: Collection<String>,
        createdAt: Long = TimeUtils.now(),
    ): Event? {
        val definition = MarmotPolls.parsePoll(poll) ?: return null
        val known = definition.options.map { it.id }
        if (optionIds.any { it !in known }) return null
        val ordered = known.filter { it in optionIds }
        if (!MarmotPolls.isValidResponse(definition, createdAt, ordered)) return null
        val tags = MarmotPolls.responseTags(poll.id, ordered) ?: return null
        val template = EventTemplate<Event>(createdAt, MarmotPolls.KIND_POLL_RESPONSE, tags, "")
        return RumorAssembler.assembleRumor(author, template)
    }
}
