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

import com.vitorpamplona.marmot.foundation.appEvents.MarmotMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * The `p` tags a Marmot message gives the accounts its text mentions, as MDK's sender adds them:
 * one per `nostr:npub1…`, `nostr:nprofile1…` or `@npub1…`, read by marmot-kmp's own scan
 * ([MarmotMessageReferences.mentions]) so what we tag is what every receiver counts.
 *
 * For the send paths that have no composer tagger (amy, a notification's inline reply) or whose
 * rumor builder takes no mentions (a media caption).
 */
object MarmotMentionTags {
    /** A `p` for every account [text] mentions inline, in first-seen order. */
    fun of(text: String): List<PTag> = MarmotMessageReferences.mentions(scanOnly(text)).map { PTag(it) }

    /**
     * [rumor] with a `p` for each account its content mentions that no `p` names yet, re-assembled
     * (so its id) when one was added; [rumor] itself when none was.
     */
    fun added(rumor: Event): Event {
        val tagged = rumor.tags.mapNotNullTo(HashSet()) { tag -> tag.getOrNull(1)?.takeIf { tag[0] == "p" } }
        val missing = of(rumor.content).filter { it.pubKey !in tagged }
        if (missing.isEmpty()) return rumor
        val tags = rumor.tags + missing.map { it.toTagArray() }
        return RumorAssembler.assembleRumor<Event>(rumor.pubKey, rumor.createdAt, rumor.kind, tags, rumor.content)
    }

    /** A kind 9 that only carries [text]: the scan reads a chat message's content and nothing else of it. */
    private fun scanOnly(text: String) = Event("", "", 0, ChatEvent.KIND, emptyArray(), text, "")
}
