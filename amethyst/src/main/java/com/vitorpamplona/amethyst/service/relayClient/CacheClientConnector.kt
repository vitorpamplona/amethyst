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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayInsertConfirmationCollector
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

class CacheClientConnector(
    val client: INostrClient,
    val cache: LocalCache,
) {
    val receiver =
        EventCollector(client) { event, relay ->
            cache.justConsume(event, relay, false)
        }

    val confirmationWatcher =
        RelayInsertConfirmationCollector(client) { eventId, relay ->
            cache.markAsSeen(eventId, relay.url)
            markAsSeen(eventId, relay.url)
        }

    fun destroy() {
        receiver.destroy()
        confirmationWatcher.destroy()
    }

    private fun markAsSeen(
        eventId: HexKey,
        info: NormalizedRelayUrl,
    ) {
        val note = LocalCache.getNoteIfExists(eventId)
        if (note != null) {
            note.addRelay(info)
            markAsSeenInner(note, info)
        }
    }

    private fun markAsSeenInner(
        note: Note,
        info: NormalizedRelayUrl,
    ) {
        val noteEvent = note.event
        if (noteEvent is GiftWrapEvent) {
            val innerEvent = noteEvent.innerEventId
            if (innerEvent != null) {
                val innerNote = cache.getNoteIfExists(innerEvent)
                if (innerNote != null) {
                    innerNote.addRelay(info)
                    markAsSeenInner(innerNote, info)
                }
            }
        }

        if (noteEvent is SealedRumorEvent) {
            val innerEvent = noteEvent.innerEventId
            if (innerEvent != null) {
                val innerNote = cache.getNoteIfExists(innerEvent)
                if (innerNote != null) {
                    innerNote.addRelay(info)
                    markAsSeenInner(innerNote, info)
                }
            }
        }
    }
}
