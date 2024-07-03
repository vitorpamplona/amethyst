/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.ammolite.relays.NostrDataSource
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.Event

abstract class AmethystNostrDataSource(
    debugName: String,
) : NostrDataSource(debugName) {
    override fun consume(
        event: Event,
        relay: Relay,
    ) {
        LocalCache.verifyAndConsume(event, relay)
    }

    override fun markAsSeenOnRelay(
        eventId: String,
        relay: Relay,
    ) {
        val note = LocalCache.getNoteIfExists(eventId)
        val noteEvent = note?.event
        if (noteEvent is AddressableEvent) {
            LocalCache.getAddressableNoteIfExists(noteEvent.address().toTag())?.addRelay(relay)
        } else {
            note?.addRelay(relay)
        }
    }
}
