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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.datasources.EventCollector
import com.vitorpamplona.ammolite.relays.datasources.RelayInsertConfirmationCollector
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

class CacheClientConnector(
    val client: NostrClient,
    val cache: LocalCache,
) {
    val receiver =
        EventCollector(client) { event, relay ->
            cache.verifyAndConsume(event, relay)
        }

    val confirmationWatcher =
        RelayInsertConfirmationCollector(client) { eventId, relay ->
            cache.markAsSeen(eventId, relay)
            unwrapAndMarkAsSeen(eventId, relay)
        }

    fun destroy() {
        receiver.destroy()
        confirmationWatcher.destroy()
    }

    private fun unwrapAndMarkAsSeen(
        eventId: HexKey,
        relay: Relay,
    ) {
        val note = LocalCache.getNoteIfExists(eventId)

        if (note != null) {
            note.addRelay(relay)

            val noteEvent = note.event
            if (noteEvent is GiftWrapEvent) {
                noteEvent.innerEventId?.let {
                    unwrapAndMarkAsSeen(it, relay)
                }
            } else if (noteEvent is SealedRumorEvent) {
                noteEvent.innerEventId?.let {
                    unwrapAndMarkAsSeen(it, relay)
                }
            }
        }
    }
}
