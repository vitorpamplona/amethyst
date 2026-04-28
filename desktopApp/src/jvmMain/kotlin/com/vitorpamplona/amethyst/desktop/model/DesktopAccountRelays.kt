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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.prefs.Preferences

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
    private val prefs = Preferences.userNodeForPackage(DesktopAccountRelays::class.java)

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

    init {
        loadFromPersistence()
    }

    private fun prefsKey(kind: Int) = "relay_${kind}_${userPubKeyHex.take(16)}"

    private fun saveEvent(
        kind: Int,
        event: Event,
    ) {
        try {
            val json = event.toJson()
            if (json.length > MAX_PREFS_VALUE_LENGTH) return // Preferences 8KB limit
            prefs.put(prefsKey(kind), json)
        } catch (_: Exception) {
            // Best-effort persistence
        }
    }

    companion object {
        private const val MAX_PREFS_VALUE_LENGTH = 8000 // java.util.prefs limit is 8192
    }

    private fun loadEvent(kind: Int): Event? =
        try {
            val json = prefs.get(prefsKey(kind), null) ?: return null
            val event = Event.fromJson(json)
            if (event.kind == kind && event.pubKey == userPubKeyHex) event else null
        } catch (_: Exception) {
            null
        }

    private fun loadFromPersistence() {
        // Load DM relays from event or URL cache
        val dmEvent = loadEvent(ChatMessageRelayListEvent.KIND)
        if (dmEvent is ChatMessageRelayListEvent) {
            _dmRelayList.value = dmEvent.relays().toSet()
            lastDmCreatedAt.set(dmEvent.createdAt)
        } else {
            loadRelayUrls("dm").let { if (it.isNotEmpty()) _dmRelayList.value = it }
        }

        // Load search relays from event or URL cache
        val searchEvent = loadEvent(SearchRelayListEvent.KIND)
        if (searchEvent is SearchRelayListEvent) {
            val relays = searchEvent.publicRelays().toSet()
            if (relays.isNotEmpty()) {
                _searchRelayList.value = relays
                lastSearchCreatedAt.set(searchEvent.createdAt)
            } else {
                // Public tags empty — try URL cache (NIP-51 private tags can't be decrypted here)
                loadRelayUrls("search").let { if (it.isNotEmpty()) _searchRelayList.value = it }
            }
        } else {
            loadRelayUrls("search").let { if (it.isNotEmpty()) _searchRelayList.value = it }
        }

        // Load blocked relays — always use URL cache (private tags can't be decrypted synchronously)
        loadRelayUrls("blocked").let { if (it.isNotEmpty()) _blockedRelayList.value = it }
    }

    /**
     * Routes relay config events (kinds 10050, 10007, 10006) to the appropriate handler.
     * Returns true if the event was consumed.
     * Uses created_at checking to prevent stale overwrites.
     */
    fun consumeIfRelevant(event: Event): Boolean {
        if (event.pubKey != userPubKeyHex) return false
        return when (event.kind) {
            AdvertisedRelayListEvent.KIND -> {
                // Persist NIP-65 event for restart survival (state managed by Nip65RelayListState)
                saveEvent(event.kind, event)
                true
            }

            ChatMessageRelayListEvent.KIND -> {
                if (event is ChatMessageRelayListEvent && event.createdAt >= lastDmCreatedAt.get()) {
                    lastDmCreatedAt.set(event.createdAt)
                    val relays = event.relays().toSet()
                    _dmRelayList.value = relays
                    saveEvent(event.kind, event)
                    saveRelayUrls("dm", relays)
                }
                true
            }

            SearchRelayListEvent.KIND -> {
                if (event is SearchRelayListEvent && event.createdAt >= lastSearchCreatedAt.get()) {
                    lastSearchCreatedAt.set(event.createdAt)
                    val relays = event.publicRelays().toSet()
                    _searchRelayList.value = relays
                    saveEvent(event.kind, event)
                    saveRelayUrls("search", relays)
                }
                true
            }

            BlockedRelayListEvent.KIND -> {
                if (event is BlockedRelayListEvent && event.createdAt >= lastBlockedCreatedAt.get()) {
                    lastBlockedCreatedAt.set(event.createdAt)
                    // publicRelays() may be empty for NIP-51 private-tag events
                    val relays = event.publicRelays().toSet()
                    _blockedRelayList.value = relays
                    saveEvent(event.kind, event)
                    if (relays.isNotEmpty()) saveRelayUrls("blocked", relays)
                }
                true
            }

            else -> {
                false
            }
        }
    }

    /** Called after publishing a relay list event from the UI — updates local state + persists */
    fun consumePublishedEvent(event: Event) {
        consumeIfRelevant(event)
    }

    /** Load persisted NIP-65 event for Nip65RelayListState backup */
    fun loadPersistedNip65Event(): AdvertisedRelayListEvent? {
        val event = loadEvent(AdvertisedRelayListEvent.KIND)
        return event as? AdvertisedRelayListEvent
    }

    fun setDmRelays(relays: Set<NormalizedRelayUrl>) {
        lastDmCreatedAt.set(TimeUtils.now())
        _dmRelayList.value = relays
        saveRelayUrls("dm", relays)
    }

    fun setSearchRelays(relays: Set<NormalizedRelayUrl>) {
        lastSearchCreatedAt.set(TimeUtils.now())
        _searchRelayList.value = relays
        saveRelayUrls("search", relays)
    }

    fun setBlockedRelays(relays: Set<NormalizedRelayUrl>) {
        lastBlockedCreatedAt.set(TimeUtils.now())
        _blockedRelayList.value = relays
        saveRelayUrls("blocked", relays)
    }

    private fun saveRelayUrls(
        category: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        try {
            val key = "urls_${category}_${userPubKeyHex.take(16)}"
            prefs.put(key, relays.joinToString(",") { it.url })
            prefs.flush()
        } catch (_: Exception) {
        }
    }

    private fun loadRelayUrls(category: String): Set<NormalizedRelayUrl> {
        val key = "urls_${category}_${userPubKeyHex.take(16)}"
        val csv = prefs.get(key, "") ?: return emptySet()
        if (csv.isBlank()) return emptySet()
        return csv.split(",").mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }.toSet()
    }
}
