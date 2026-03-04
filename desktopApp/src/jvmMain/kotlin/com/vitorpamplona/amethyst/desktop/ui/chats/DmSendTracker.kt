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
package com.vitorpamplona.amethyst.desktop.ui.chats

import com.vitorpamplona.amethyst.commons.ui.chat.DmBroadcastStatus
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponseDetailed
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DmSendTracker(
    private val client: INostrClient,
) {
    private val _status = MutableStateFlow<DmBroadcastStatus>(DmBroadcastStatus.Idle)
    val status: StateFlow<DmBroadcastStatus> = _status.asStateFlow()

    /**
     * Sends multiple event/relay pairs (e.g. NIP-17 wraps) and aggregates results.
     * Shows per-relay success/failure across all wraps.
     */
    suspend fun sendBatch(events: List<Pair<Event, Set<NormalizedRelayUrl>>>) {
        if (events.isEmpty()) return

        val totalRelays = events.sumOf { it.second.size }
        if (totalRelays == 0) {
            _status.value = DmBroadcastStatus.Failed("No relays available")
            delay(3000)
            _status.value = DmBroadcastStatus.Idle
            return
        }

        _status.value = DmBroadcastStatus.Sending(0, totalRelays)

        val allSuccessful = mutableSetOf<NormalizedRelayUrl>()

        for ((event, relays) in events) {
            val results = client.sendAndWaitForResponseDetailed(event, relays, 10)
            allSuccessful.addAll(results.filter { it.value }.keys)
            _status.value = DmBroadcastStatus.Sending(allSuccessful.size, totalRelays)
        }

        _status.value =
            if (allSuccessful.isNotEmpty()) {
                DmBroadcastStatus.Sent(
                    relayCount = allSuccessful.size,
                    relayUrls = allSuccessful.map { it.url },
                )
            } else {
                DmBroadcastStatus.Failed("No relay accepted the message")
            }

        delay(3000)
        _status.value = DmBroadcastStatus.Idle
    }

    /**
     * Sends a single event to relays with tracking.
     */
    suspend fun sendAndTrack(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ) {
        sendBatch(listOf(event to relays))
    }
}
