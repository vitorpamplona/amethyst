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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages relay state for a desktop account.
 *
 * Bridges the gap between Android's Account.dmRelays (which depends on
 * LocalCache, AccountSettings, and multiple relay list states) and desktop's
 * simpler relay management.
 *
 * On Android, Account.dmRelays aggregates:
 * - DmRelayListState (ChatMessageRelayListEvent, kind 10050)
 * - Nip65RelayListState (NIP-65 advertised relays)
 * - PrivateStorageRelayListState
 * - LocalRelayListState
 *
 * On Desktop, we start with the DM relay list and connected relays as fallback.
 * As the desktop Account evolves, this class will grow to match Android's behavior.
 */
class DesktopAccountRelays(
    val userPubKeyHex: HexKey,
    relayManager: RelayConnectionManager,
    scope: CoroutineScope,
) {
    /** User-configured DM relays from kind 10050 events */
    private val _dmRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val dmRelayList: StateFlow<Set<NormalizedRelayUrl>> = _dmRelayList.asStateFlow()

    /** Aggregated DM relay state (DM relays + fallback to connected relays) */
    val dmRelays =
        DesktopDmRelayState(
            dmRelayList = _dmRelayList,
            connectedRelays = relayManager.connectedRelays,
            scope = scope,
        )

    /**
     * Processes a ChatMessageRelayListEvent (kind 10050) to update DM relays.
     * Call this when receiving kind 10050 events from relay subscriptions.
     */
    fun consumeDmRelayList(event: ChatMessageRelayListEvent) {
        if (event.pubKey != userPubKeyHex) return
        val relays = event.relays().toSet()
        _dmRelayList.value = relays
    }

    /**
     * Processes any event that might be a DM relay list.
     * Returns true if the event was consumed as a DM relay list.
     */
    fun consumeIfDmRelayList(event: Event): Boolean {
        if (event.kind == ChatMessageRelayListEvent.KIND && event is ChatMessageRelayListEvent) {
            consumeDmRelayList(event)
            return true
        }
        return false
    }

    /**
     * Manually sets DM relays (e.g., from saved preferences).
     */
    fun setDmRelays(relays: Set<NormalizedRelayUrl>) {
        _dmRelayList.value = relays
    }
}
