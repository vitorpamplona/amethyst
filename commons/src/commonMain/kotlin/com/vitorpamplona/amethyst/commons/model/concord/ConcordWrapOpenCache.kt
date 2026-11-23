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
package com.vitorpamplona.amethyst.commons.model.concord

import androidx.collection.LruCache
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.concord.envelope.StreamWrapOpener
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * The opened header (seal and rumor) of each Concord stream wrap a session re-reads, by wrap id.
 *
 * The rekey drains (base and channel rotations, CORD-06) run on every revision tick and judge the
 * session's whole rekey buffer each time, because a rotation that was not takeable (missing chunks,
 * a Grant not folded yet) can become takeable. Each judgement used to open every buffered wrap again,
 * and the channel drain did it once per held Private Channel: a signature check and two NIP-44
 * decryptions per wrap per channel per tick, for results that never change.
 *
 * Bounded ([maxEntries], least recently used out), so a long-lived session holding a large buffer
 * costs at most one re-open per evicted wrap. A wrap that does not open is remembered too. Keyed by
 * wrap id and stream address: a wrap only opens at its own address, and an address has one key.
 */
class ConcordWrapOpenCache(
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : StreamWrapOpener {
    private class Opened(
        val event: OpenedStreamEvent?,
    )

    private val cache = LruCache<String, Opened>(maxEntries)

    /** How many wraps were actually opened (cache misses). */
    fun opens(): Int = cache.missCount()

    override fun openOrNull(
        wrap: Event,
        stream: GroupKey,
    ): OpenedStreamEvent? {
        val key = wrap.id + stream.publicKeyHex
        cache[key]?.let { return it.event }
        val opened = ConcordStreamEnvelope.openOrNull(wrap, stream)
        cache.put(key, Opened(opened))
        return opened
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 2048
    }
}
