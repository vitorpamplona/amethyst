/**
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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow

class PoolEventOutboxRepository {
    private var eventOutbox = mapOf<HexKey, PoolEventOutbox>()
    val relays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    fun updateRelays() {
        val myRelays = mutableSetOf<NormalizedRelayUrl>()
        eventOutbox.values.forEach {
            myRelays.addAll(it.relaysLeft())
        }

        if (relays.value != myRelays) {
            relays.tryEmit(myRelays)
        }
    }

    fun markAsSending(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ) {
        val currentOutbox = eventOutbox[event.id]
        if (currentOutbox == null) {
            eventOutbox = eventOutbox + Pair(event.id, PoolEventOutbox(event, relays))
        } else {
            currentOutbox.updateRelays(relays)
        }
        updateRelays()
    }

    fun newTry(
        id: HexKey,
        url: NormalizedRelayUrl,
    ) {
        eventOutbox[id]?.newTry(url)
    }

    fun newResponse(
        id: HexKey,
        url: NormalizedRelayUrl,
        success: Boolean,
        message: String,
    ) {
        val waiting = eventOutbox[id]
        if (waiting != null) {
            waiting.newResponse(url, success, message)
            clear()
        }
    }

    fun clear() {
        eventOutbox = eventOutbox.filter { !it.value.isDone() }
        updateRelays()
    }

    fun forEachUnsentEvent(
        url: NormalizedRelayUrl,
        run: (url: Event) -> Unit,
    ) {
        eventOutbox.forEach {
            it.value.forEachUnsentEvent(url, run)
        }
    }
}
