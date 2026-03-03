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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Desktop equivalent of DmInboxRelayState.
 *
 * Aggregates DM inbox relays from multiple sources:
 * - DM relay list (NIP-17 ChatMessageRelayListEvent, kind 10050)
 * - Connected relays (fallback when no DM-specific relays are configured)
 *
 * On Android, the full Account class manages four relay sources:
 * - NIP-65 advertised inbox relays
 * - NIP-17 DM relay list
 * - Private storage outbox relays
 * - Local relays
 *
 * Desktop simplifies this: we combine the user's DM relay list with
 * the connected relays as a fallback. As the desktop Account evolves,
 * additional relay sources can be added here.
 */
class DesktopDmRelayState(
    dmRelayList: StateFlow<Set<NormalizedRelayUrl>>,
    connectedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    scope: CoroutineScope,
) {
    /**
     * Combined DM relay set.
     * Prefers DM-specific relays when available, falls back to connected relays.
     */
    val flow: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            dmRelayList,
            connectedRelays,
        ) { dmRelays, connected ->
            if (dmRelays.isNotEmpty()) {
                dmRelays
            } else {
                // Fallback: use all connected relays when no DM relays are configured
                connected
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                dmRelayList.value.ifEmpty { connectedRelays.value },
            )

    /**
     * Outbox relays for sending DMs FROM the user.
     * Uses the connected relays (home relay equivalent on desktop).
     */
    val outboxFlow: StateFlow<Set<NormalizedRelayUrl>> = connectedRelays
}
