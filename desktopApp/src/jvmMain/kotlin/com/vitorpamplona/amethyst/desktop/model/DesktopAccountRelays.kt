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
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

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

    /** User-configured search relays from kind 10007 events */
    private val _searchRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val searchRelayList: StateFlow<Set<NormalizedRelayUrl>> = _searchRelayList.asStateFlow()

    /** User-configured blocked relays from kind 10006 events */
    private val _blockedRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val blockedRelayList: StateFlow<Set<NormalizedRelayUrl>> = _blockedRelayList.asStateFlow()

    // Track created_at to prevent stale overwrites (thread-safe)
    private val lastDmCreatedAt = AtomicLong(0L)
    private val lastSearchCreatedAt = AtomicLong(0L)
    private val lastBlockedCreatedAt = AtomicLong(0L)

    /** Aggregated DM relay state (DM relays + fallback to connected relays) */
    val dmRelays =
        DesktopDmRelayState(
            dmRelayList = _dmRelayList,
            connectedRelays = relayManager.connectedRelays,
            scope = scope,
        )

    /** Routes kind 10050 to DM relay state. Use consumeIfRelevant() for external callers. */
    private fun consumeDmRelayList(event: ChatMessageRelayListEvent) {
        if (event.pubKey != userPubKeyHex) return
        _dmRelayList.value = event.relays().toSet()
    }

    /**
     * Routes relay config events (kinds 10050, 10007, 10006) to the appropriate handler.
     * Returns true if the event was consumed.
     * Uses created_at checking to prevent stale overwrites.
     */
    fun consumeIfRelevant(event: Event): Boolean {
        if (event.pubKey != userPubKeyHex) return false
        return when (event.kind) {
            ChatMessageRelayListEvent.KIND -> {
                if (event is ChatMessageRelayListEvent && event.createdAt > lastDmCreatedAt.get()) {
                    lastDmCreatedAt.set(event.createdAt)
                    _dmRelayList.value = event.relays().toSet()
                }
                true
            }

            SearchRelayListEvent.KIND -> {
                if (event is SearchRelayListEvent && event.createdAt > lastSearchCreatedAt.get()) {
                    lastSearchCreatedAt.set(event.createdAt)
                    _searchRelayList.value = event.publicRelays().toSet()
                }
                true
            }

            BlockedRelayListEvent.KIND -> {
                if (event is BlockedRelayListEvent && event.createdAt > lastBlockedCreatedAt.get()) {
                    lastBlockedCreatedAt.set(event.createdAt)
                    _blockedRelayList.value = event.publicRelays().toSet()
                }
                true
            }

            else -> {
                false
            }
        }
    }

    /**
     * Manually sets DM relays (e.g., from saved preferences).
     */
    fun setDmRelays(relays: Set<NormalizedRelayUrl>) {
        lastDmCreatedAt.set(Long.MAX_VALUE)
        _dmRelayList.value = relays
    }

    fun setSearchRelays(relays: Set<NormalizedRelayUrl>) {
        lastSearchCreatedAt.set(Long.MAX_VALUE)
        _searchRelayList.value = relays
    }

    fun setBlockedRelays(relays: Set<NormalizedRelayUrl>) {
        lastBlockedCreatedAt.set(Long.MAX_VALUE)
        _blockedRelayList.value = relays
    }
}
