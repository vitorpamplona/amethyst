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
package com.vitorpamplona.amethyst.commons.model.nip59Giftwrap

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.HostStub
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * Rumor id → the envelope that delivered it (normally the kind-1059 gift
 * wrap; a bare kind-13 seal when one arrives unwrapped).
 *
 * Unsealed rumors are unsigned and must never be referenced or republished
 * directly on public relays. Consumers use this index to act on the
 * delivering envelope instead: broadcast republishes the wrap, nevent
 * citations point at the wrap id, chat pruning pages by the outer wrap
 * time, and cache eviction removes the envelope notes alongside the rumor.
 *
 * Delivery metadata is deliberately kept OUT of the quartz event classes —
 * any event kind can be a rumor without subclassing anything. Populated by
 * each front end's gift-wrap ingestion pipeline.
 */
object RumorHosts {
    private val index = LargeCache<HexKey, HostStub>()

    fun put(
        rumorId: HexKey,
        host: HostStub,
    ) = index.put(rumorId, host)

    /** Records [envelope] as the delivering event of [rumorId]. */
    fun put(
        rumorId: HexKey,
        envelope: Event,
    ) = index.put(rumorId, HostStub(envelope.id, envelope.pubKey, envelope.kind, envelope.createdAt))

    fun get(rumorId: HexKey): HostStub? = index.get(rumorId)

    /** The delivering envelope of [event], when [event] is a rumor. */
    fun of(event: Event): HostStub? = if (event.sig.isEmpty()) index.get(event.id) else null

    fun remove(rumorId: HexKey) {
        index.remove(rumorId)
    }

    fun clear() = index.clear()
}
