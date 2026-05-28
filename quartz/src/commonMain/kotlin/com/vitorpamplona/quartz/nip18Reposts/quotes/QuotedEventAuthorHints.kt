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
package com.vitorpamplona.quartz.nip18Reposts.quotes

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.utils.Hex

/**
 * Extracts (event-id, author-pubkey) hints for events that this event quotes,
 * by reading only the tag set — no content parsing, no signature check.
 *
 * The output lets a client populate the author slot of a quoted-note
 * placeholder before the quoted event itself has been fetched. That author
 * is what unlocks NIP-65 outbox routing for the fetch — without it, clients
 * fall back to the user's own write relays and miss notes that live on the
 * quoted author's relays (e.g. Momostr-bridged accounts whose only write
 * relay is `relay.momostr.pink`).
 *
 * Three recognised forms, applied in priority order:
 *  - NIP-18 q-tag with inline pubkey: `["q", "<id>", "<relay>", "<pubkey>"]`
 *  - NIP-10 e-tag with mention marker and inline pubkey:
 *    `["e", "<id>", "<relay>", "mention", "<pubkey>"]`
 *  - NIP-10 paired e-mention + p-mention tags, only when there is exactly
 *    one of each. NIP-10 makes no positional-pairing guarantee, so we only
 *    trust the pairing in the unambiguous single-quote case — which is what
 *    Momostr-bridged quote posts emit and what causes the original bug.
 *
 * All hex ids are validated as 64-char hex (Hex.isHex64) before being
 * emitted — a 64-char garbage value otherwise crashes downstream callers
 * that `require(isValidHex(...))` (e.g. LocalCache.getOrCreateNote).
 *
 * Output is deduplicated by event id, first-write-wins, so the priority
 * order above is respected when a single event id is hinted from multiple
 * tag forms.
 */
object QuotedEventAuthorHints {
    data class Hint(
        val eventId: HexKey,
        val authorPubKey: HexKey,
    )

    fun collect(tags: Array<Array<String>>): List<Hint> {
        val out = LinkedHashMap<HexKey, HexKey>()

        // 1) NIP-18 q-tags with inline author
        for (tag in tags) {
            if (tag.size >= 4 &&
                tag[0] == "q" &&
                isValidHexKey(tag[1]) &&
                isValidHexKey(tag[3])
            ) {
                if (!out.containsKey(tag[1])) out[tag[1]] = tag[3]
            }
        }

        // Parse all NIP-10 e-tags via MarkedETag so we inherit every layout
        // variant the upstream parser accepts — including the swapped form
        // `["e","<id>","<relay>","<pubkey>","mention"]` that the hand-rolled
        // index checks used to miss. parseAllThreadTags is the lenient parser
        // (it doesn't require a non-empty relay slot, which is the Momostr
        // bridge shape).
        val eMentions =
            tags
                .mapNotNull(MarkedETag::parseAllThreadTags)
                .filter { it.marker == MarkedETag.MARKER.MENTION && isValidHexKey(it.eventId) }

        // 2) e-mention tags that carry an inline author pubkey
        for (mention in eMentions) {
            val author = mention.author
            if (author != null && isValidHexKey(author)) {
                if (!out.containsKey(mention.eventId)) out[mention.eventId] = author
            }
        }

        // 3) NIP-10 paired e-mention + p-mention, ONLY when exactly one of
        //    each exists in the tag set. Anything beyond that ratio is
        //    ambiguous (cc-style p-mentions, multi-quote posts, label/raid
        //    semantics) and positional pairing produces false attributions.
        val pMentions = mutableListOf<HexKey>()
        for (tag in tags) {
            if (tag.size >= 4 && tag[0] == "p" && isValidHexKey(tag[1]) && tag[3] == "mention") {
                pMentions.add(tag[1])
            }
        }
        if (eMentions.size == 1 && pMentions.size == 1) {
            val eventId = eMentions[0].eventId
            if (!out.containsKey(eventId)) out[eventId] = pMentions[0]
        }

        return out.map { (eventId, authorPubKey) -> Hint(eventId, authorPubKey) }
    }

    private fun isValidHexKey(value: String): Boolean = value.length == 64 && Hex.isHex64(value)
}
