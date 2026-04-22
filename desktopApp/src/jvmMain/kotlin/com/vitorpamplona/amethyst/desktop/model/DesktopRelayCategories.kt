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

import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

/**
 * Aggregates relay categories for desktop subscriptions.
 *
 * Each category combines user-configured relays with fallbacks and subtracts blocked relays.
 * Debounced to prevent subscription thrashing at startup.
 */
@OptIn(FlowPreview::class)
class DesktopRelayCategories(
    nip65State: Nip65RelayListState,
    accountRelays: DesktopAccountRelays,
    connectedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    scope: CoroutineScope,
) {
    /** NIP-65 outbox (write) relays, falls back to connected relays, minus blocked */
    val feedRelays: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            nip65State.outboxFlow,
            connectedRelays,
            accountRelays.blockedRelayList,
        ) { outbox, connected, blocked ->
            (outbox.ifEmpty { connected }) - blocked
        }.debounce(1.seconds)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, connectedRelays.value)

    /** NIP-65 inbox (read) relays, falls back to connected relays, minus blocked */
    val notificationRelays: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            nip65State.inboxFlow,
            connectedRelays,
            accountRelays.blockedRelayList,
        ) { inbox, connected, blocked ->
            (inbox.ifEmpty { connected }) - blocked
        }.debounce(1.seconds)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, connectedRelays.value)

    /** Search relays (kind 10007), falls back to relay.nostr.band, minus blocked */
    val searchRelays: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            accountRelays.searchRelayList,
            accountRelays.blockedRelayList,
        ) { search, blocked ->
            (search.ifEmpty { DEFAULT_SEARCH_RELAYS }) - blocked
        }.debounce(1.seconds)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SEARCH_RELAYS)

    /** DM relays — aggregated DM state minus blocked */
    val dmRelays: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            accountRelays.dmRelays.flow,
            accountRelays.blockedRelayList,
        ) { dm, blocked -> dm - blocked }
            .debounce(1.seconds)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, accountRelays.dmRelays.flow.value)

    companion object {
        val DEFAULT_SEARCH_RELAYS =
            setOfNotNull(RelayUrlNormalizer.normalizeOrNull("wss://relay.nostr.band"))
    }
}
