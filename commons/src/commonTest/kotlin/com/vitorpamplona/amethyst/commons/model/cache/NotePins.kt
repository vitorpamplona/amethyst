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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Consumes events into a cache while holding their notes for the test's lifetime.
 *
 * The cache holds notes weakly (LargeSoftCache), so a note nothing else references can be
 * collected between consuming an event and the assertion that reads it: a lookup comes back null,
 * a walk comes back short, and an `e` to an addressable version finds an empty note it cannot
 * upgrade to the address. Keep one per test class, as a field, so it lives as long as the test.
 */
internal class NotePins {
    private val notes = mutableListOf<Note>()

    /**
     * Pins [event]'s note by id and the note at [address], then consumes it. [address] defaults to
     * an [AddressableEvent]'s own; pass it for an event quartz has no class for.
     */
    fun consume(
        cache: EventCache,
        event: Event,
        relay: NormalizedRelayUrl? = null,
        wasVerified: Boolean = true,
        address: Address? = (event as? AddressableEvent)?.address(),
    ): Boolean {
        if (address != null) notes.add(cache.getOrCreateAddressableNote(address))
        notes.add(cache.getOrCreateNote(event.id))
        return cache.justConsume(event, relay, wasVerified)
    }
}
