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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayCsvLoader
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.amethyst.service.geohash.GeohashChatDeviceSeed
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives a single Bitchat-interoperable geohash location chat.
 *
 * Ephemeral kind-20000 messages (and kind-20001 presence) for the cell are
 * routed to the geographically-nearest relays ([GeoRelayDirectory]); because the
 * events are ephemeral the relays broadcast but do not store them, so this holds
 * a live subscription for as long as the screen is open. Outgoing messages are
 * signed with the device's per-geohash throwaway identity and carry a small
 * NIP-13 proof of work so relays that rate-limit geohash chat let them through.
 *
 * Follows the codebase convention of a no-arg ViewModel initialized once via
 * [init] from the composable.
 */
class GeohashChatViewModel : ViewModel() {
    lateinit var geohash: String
        private set

    private lateinit var accountViewModel: AccountViewModel

    private val _messages = MutableStateFlow<List<GeohashChatEvent>>(emptyList())
    val messages: StateFlow<List<GeohashChatEvent>> = _messages.asStateFlow()

    private val _relays = MutableStateFlow<List<NormalizedRelayUrl>>(emptyList())
    val relays: StateFlow<List<NormalizedRelayUrl>> = _relays.asStateFlow()

    private val _participants = MutableStateFlow(0)
    val participants: StateFlow<Int> = _participants.asStateFlow()

    private val seen = HashSet<String>()
    private val present = HashSet<String>()
    private val subId = newSubId()
    private var started = false
    private var subscribed = false

    fun init(
        geohash: String,
        accountViewModel: AccountViewModel,
    ) {
        if (started) return
        started = true
        this.geohash = geohash
        this.accountViewModel = accountViewModel
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val relays = resolveRelays()
        _relays.value = relays
        if (relays.isEmpty()) return

        val since = TimeUtils.now() - INITIAL_LOOKBACK_SECS
        val filter =
            Filter(
                kinds = listOf(GeohashChatEvent.KIND, GeohashPresenceEvent.KIND),
                tags = mapOf("g" to listOf(geohash)),
                since = since,
                limit = 500,
            )
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ingest(event)
                }
            }
        accountViewModel.account.client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)
        subscribed = true
    }

    private fun ingest(event: Event) {
        if (!event.verify()) return
        if (event.geohashOrNull() != geohash) return
        if (!seen.add(event.id)) return
        when (event) {
            is GeohashChatEvent -> {
                if (present.add(event.pubKey)) _participants.value = present.size
                _messages.update { current -> (current + event).sortedBy { it.createdAt } }
            }

            is GeohashPresenceEvent -> {
                if (present.add(event.pubKey)) _participants.value = present.size
            }
        }
    }

    private fun Event.geohashOrNull(): String? =
        when (this) {
            is GeohashChatEvent -> geohash()
            is GeohashPresenceEvent -> geohash()
            else -> null
        }

    /** Build, mine a small PoW, sign with the per-geohash identity, and publish. */
    fun sendMessage(
        text: String,
        nickname: String?,
        teleported: Boolean = false,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val relays = _relays.value.toSet()
        if (relays.isEmpty()) return

        viewModelScope.launch {
            val keyPair = withContext(Dispatchers.IO) { GeohashChatDeviceSeed.keyPair(geohash) }
            val signer = NostrSignerInternal(keyPair)

            var template = GeohashChatEvent.build(trimmed, geohash, nickname = nickname?.ifBlank { null }, teleported = teleported)
            template =
                withContext(Dispatchers.Default) {
                    val deadline = System.nanoTime() + POW_TIMEOUT_NANOS
                    runCatching {
                        PoWMiner.mine(template, keyPair.pubKey.toHexKey(), POW_BITS, powThreads()) { System.nanoTime() < deadline }
                    }.getOrDefault(template)
                }

            runCatching { accountViewModel.account.signWithAndSendPrivately(template, signer, relays) }
        }
    }

    /** Announce presence in the cell (a bare kind-20001 heartbeat). */
    fun announcePresence(nickname: String?) {
        val relays = _relays.value.toSet()
        if (relays.isEmpty()) return
        viewModelScope.launch {
            val keyPair = withContext(Dispatchers.IO) { GeohashChatDeviceSeed.keyPair(geohash) }
            val signer = NostrSignerInternal(keyPair)
            val template = GeohashPresenceEvent.build(geohash, nickname = nickname?.ifBlank { null })
            runCatching { accountViewModel.account.signWithAndSendPrivately(template, signer, relays) }
        }
    }

    private suspend fun resolveRelays(): List<NormalizedRelayUrl> {
        if (!refreshed) {
            runCatching {
                GeoRelayCsvLoader { Amethyst.instance.okHttpClients.getHttpClient(false) }.refresh(sharedDirectory)
            }
            refreshed = sharedDirectory.size > GeoRelayDirectory.FALLBACK.size
        }
        return sharedDirectory.closestRelays(geohash)
    }

    override fun onCleared() {
        if (subscribed) runCatching { accountViewModel.account.client.unsubscribe(subId) }
        super.onCleared()
    }

    companion object {
        private const val INITIAL_LOOKBACK_SECS = 60L * 60L
        private const val POW_BITS = 8
        private const val POW_TIMEOUT_NANOS = 2_000_000_000L

        private fun powThreads(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        /** Process-wide directory, refreshed once from the live CSV then reused across channels. */
        private val sharedDirectory = GeoRelayDirectory()

        @Volatile private var refreshed = false
    }
}
