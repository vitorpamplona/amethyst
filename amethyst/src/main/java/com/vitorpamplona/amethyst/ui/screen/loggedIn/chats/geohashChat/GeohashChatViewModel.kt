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
import com.vitorpamplona.amethyst.service.geohash.GeohashRelays
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composer side of a Bitchat-interoperable geohash location chat.
 *
 * The message *feed* is loaded through the shared channel data path — the
 * kind-20000 subscription is assembled by
 * [ChannelFilterAssembler][com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssembler]
 * (routed to the geographically-nearest relays), stored in
 * [LocalCache][com.vitorpamplona.amethyst.model.LocalCache] on the cell's
 * [GeohashChatChannel][com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel],
 * and surfaced by
 * [ChannelFeedViewModel][com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel] —
 * exactly like every other public/ephemeral chat. This ViewModel owns only the
 * bits that are geohash-specific: resolving the cell's relays for sending, the
 * anonymous per-cell identity, the teleport / post-as-self toggles, and mining
 * the small NIP-13 proof of work on outgoing messages.
 */
class GeohashChatViewModel : ViewModel() {
    lateinit var geohash: String
        private set

    private lateinit var accountViewModel: AccountViewModel

    private val _relays = MutableStateFlow<List<NormalizedRelayUrl>>(emptyList())
    val relays: StateFlow<List<NormalizedRelayUrl>> = _relays.asStateFlow()

    /** Our own per-geohash identity pubkey, so the UI can right-align our own (anonymous) messages. */
    private val _myPubKey = MutableStateFlow<String?>(null)
    val myPubKey: StateFlow<String?> = _myPubKey.asStateFlow()

    /** Whether our outgoing messages carry the ["t","teleport"] marker (not physically in the cell). */
    private val _teleported = MutableStateFlow(false)
    val teleported: StateFlow<Boolean> = _teleported.asStateFlow()

    /**
     * When true, messages are signed with the user's REAL account key instead of the anonymous
     * per-geohash identity — trading location privacy for profile/reputation/zaps. Off by default.
     */
    private val _postAsSelf = MutableStateFlow(false)
    val postAsSelf: StateFlow<Boolean> = _postAsSelf.asStateFlow()

    private var started = false

    fun init(
        geohash: String,
        accountViewModel: AccountViewModel,
        teleported: Boolean = false,
    ) {
        if (started) return
        started = true
        this.geohash = geohash
        this.accountViewModel = accountViewModel
        _teleported.value = teleported
        viewModelScope.launch { start() }
    }

    fun setTeleported(value: Boolean) {
        _teleported.value = value
    }

    fun setPostAsSelf(value: Boolean) {
        _postAsSelf.value = value
    }

    private suspend fun start() {
        _myPubKey.value =
            withContext(Dispatchers.IO) {
                accountViewModel.account.geohashIdentity
                    .keyPair(geohash)
                    .pubKey
                    .toHexKey()
            }
        _relays.value = resolveRelays()
    }

    /** Build, mine a small PoW, sign with the per-geohash identity (or the account when opted in), and publish. */
    fun sendMessage(
        text: String,
        nickname: String?,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val relays = _relays.value.toSet().ifEmpty { resolveRelays().toSet() }
            if (relays.isEmpty()) return@launch

            val account = accountViewModel.account
            // Anonymous per-geohash identity by default; the real account only when the user opts in.
            val signer: NostrSigner
            val pubKeyHex: String
            if (_postAsSelf.value) {
                signer = account.signer
                pubKeyHex = account.signer.pubKey
            } else {
                val keyPair = withContext(Dispatchers.IO) { account.geohashIdentity.keyPair(geohash) }
                signer = NostrSignerInternal(keyPair)
                pubKeyHex = keyPair.pubKey.toHexKey()
            }

            var template = GeohashChatEvent.build(trimmed, geohash, nickname = nickname?.ifBlank { null }, teleported = _teleported.value)
            template =
                withContext(Dispatchers.Default) {
                    val deadline = System.nanoTime() + POW_TIMEOUT_NANOS
                    runCatching {
                        PoWMiner.mine(template, pubKeyHex, POW_BITS, powThreads()) { System.nanoTime() < deadline }
                    }.getOrDefault(template)
                }

            runCatching { account.signWithAndSendPrivately(template, signer, relays) }
        }
    }

    /** Announce presence in the cell (a bare kind-20001 heartbeat). */
    fun announcePresence(nickname: String?) {
        viewModelScope.launch {
            val relays = _relays.value.toSet().ifEmpty { resolveRelays().toSet() }
            if (relays.isEmpty()) return@launch
            val keyPair = withContext(Dispatchers.IO) { accountViewModel.account.geohashIdentity.keyPair(geohash) }
            val signer = NostrSignerInternal(keyPair)
            val template = GeohashPresenceEvent.build(geohash, nickname = nickname?.ifBlank { null })
            runCatching { accountViewModel.account.signWithAndSendPrivately(template, signer, relays) }
        }
    }

    private suspend fun resolveRelays(): List<NormalizedRelayUrl> {
        GeohashRelays.ensureLoaded()
        return GeohashRelays.closestRelays(geohash)
    }

    companion object {
        private const val POW_BITS = 8
        private const val POW_TIMEOUT_NANOS = 2_000_000_000L

        private fun powThreads(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }
}
