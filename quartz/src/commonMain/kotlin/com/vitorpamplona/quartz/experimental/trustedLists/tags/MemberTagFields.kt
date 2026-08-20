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
package com.vitorpamplona.quartz.experimental.trustedLists.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * The trailing fields every member tag in the family shares: index 2 is the
 * hint and index 3 the score, on `p`, `e`, `a` and `i` alike.
 */
object MemberTagFields {
    const val HINT_INDEX = 2
    const val SCORE_INDEX = 3

    /**
     * Index 2 only counts as a relay hint when it actually looks like one.
     * Publishers pad it with an empty string when they carry a score but no
     * hint, and neighbouring conventions put a petname there -- while the
     * normalizer turns any bare word into `wss://<word>/`. Without this guard
     * a petname would be indexed as a relay nobody can connect to, so this
     * mirrors the check `PTag` applies to the same slot.
     */
    fun relayHint(tag: Tag): NormalizedRelayUrl? {
        val raw = tag.getOrNull(HINT_INDEX) ?: return null
        if (raw.length < 8 || !RelayUrlNormalizer.isRelayUrl(raw)) return null
        return RelayUrlNormalizer.normalizeOrNull(raw)
    }

    /** The raw hint, for member types whose hint is not a relay url (NIP-73). */
    fun hint(tag: Tag): String? = tag.getOrNull(HINT_INDEX)?.takeIf { it.isNotEmpty() }

    fun score(tag: Tag): Int? = tag.getOrNull(SCORE_INDEX)?.toIntOrNull()
}
