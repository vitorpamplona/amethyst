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
package com.vitorpamplona.amethyst.model.nip47WalletConnect

import com.vitorpamplona.amethyst.commons.model.INwcSignerState
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentQueryState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.cache.NostrWalletConnectRequestCache
import com.vitorpamplona.quartz.nip47WalletConnect.cache.NostrWalletConnectResponseCache
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages NIP-47 (Nostr Wallet Connect) related signing operations and decryption cache for a given account.
 * Supports multiple wallets with a default wallet used for zaps.
 */
class NwcSignerState(
    val signer: NostrSigner,
    val nwcFilterAssembler: () -> NWCPaymentFilterAssembler,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
    /**
     * Fetches a wallet's kind 13194 info event so we can negotiate encryption.
     * Injected by [com.vitorpamplona.amethyst.model.Account] (which owns the relay
     * client). Null in tests / when unavailable — requests then fall back to NIP-04.
     */
    val fetchInfoEvent: (suspend (Nip47WalletConnect.Nip47URINorm) -> NwcInfoEvent?)? = null,
) : INwcSignerState {
    /**
     * Flow of the default wallet's NWC URI, derived from multi-wallet settings.
     */
    val defaultWalletUri: StateFlow<Nip47WalletConnect.Nip47URINorm?> =
        combine(settings.nwcWallets, settings.defaultPaymentSourceId) { wallets, defaultId ->
            // Use the NWC wallet the unified default points at; otherwise fall back to the
            // first NWC wallet so NWC zap routing is unchanged for NWC-only users.
            (wallets.firstOrNull { it.id == defaultId } ?: wallets.firstOrNull())?.uri
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, settings.defaultZapPaymentRequest())

    /**
     * Derives a NIP-47 signer from the default wallet configuration.
     */
    val nip47Signer =
        defaultWalletUri
            .map {
                buildSigner(it) ?: signer
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                buildSigner(defaultWalletUri.value) ?: signer,
            )

    val zapPaymentRequestDecryptionCache =
        nip47Signer
            .map {
                NostrWalletConnectRequestCache(it)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, NostrWalletConnectRequestCache(nip47Signer.value))

    val zapPaymentResponseDecryptionCache =
        nip47Signer
            .map {
                NostrWalletConnectResponseCache(it)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, NostrWalletConnectResponseCache(nip47Signer.value))

    fun buildSigner(uri: Nip47WalletConnect.Nip47URINorm?) =
        uri?.secret?.hexToByteArray()?.let {
            NostrSignerInternal(KeyPair(it))
        }

    /**
     * Per-wallet (keyed by wallet service pubkey) cache of whether the wallet
     * advertises NIP-44 (`nip44_v2`) support in its kind 13194 info event.
     * NIP-47 says a client "should always prefer nip44 if supported by the wallet
     * service"; absent/unknown means we keep the NIP-04 legacy default.
     */
    private val nip44SupportByWallet = ConcurrentHashMap<HexKey, Boolean>()

    init {
        // Warm the encryption preference in the background whenever the default
        // wallet changes so the payment hot path can read it without blocking.
        scope.launch(Dispatchers.IO) {
            defaultWalletUri
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.pubKeyHex == b.pubKeyHex && a.relayUri == b.relayUri }
                .collectLatest { warmEncryptionPreference(it) }
        }
    }

    /**
     * Fetches the wallet's info event once and records whether it supports NIP-44.
     * Best-effort: any failure leaves the wallet on the NIP-04 fallback.
     */
    private suspend fun warmEncryptionPreference(uri: Nip47WalletConnect.Nip47URINorm) {
        val fetch = fetchInfoEvent ?: return
        if (nip44SupportByWallet.containsKey(uri.pubKeyHex)) return

        val supports =
            try {
                fetch(uri)?.encryptionSchemes()?.any { it.equals("nip44_v2", ignoreCase = true) } == true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }

        nip44SupportByWallet[uri.pubKeyHex] = supports
    }

    /**
     * Non-blocking read of the negotiated encryption preference for a wallet.
     * Returns true only once the info event has been fetched and advertised
     * `nip44_v2`; otherwise NIP-04 (the legacy default).
     */
    private fun prefersNip44(uri: Nip47WalletConnect.Nip47URINorm?): Boolean {
        uri ?: return false
        return nip44SupportByWallet[uri.pubKeyHex] ?: false
    }

    fun hasWalletConnectSetup(): Boolean = settings.nwcWallets.value.isNotEmpty()

    override fun isNIP47Author(pubKey: HexKey?): Boolean = nip47Signer.value.pubKey == pubKey

    override suspend fun decryptRequest(event: LnZapPaymentRequestEvent): Request? {
        if (!hasWalletConnectSetup()) return null
        return zapPaymentRequestDecryptionCache.value.decryptRequest(event)
    }

    override suspend fun decryptResponse(event: LnZapPaymentResponseEvent): Response? {
        if (!hasWalletConnectSetup()) return null
        return zapPaymentResponseDecryptionCache.value.decryptResponse(event)
    }

    /**
     * Sends a generic NIP-47 request to the default wallet.
     */
    suspend fun sendNwcRequest(
        request: Request,
        onResponse: (Response?) -> Unit,
    ): Pair<LnZapPaymentRequestEvent, NormalizedRelayUrl> = sendNwcRequestToWallet(defaultWalletUri.value, request, onResponse)

    /**
     * Sends a generic NIP-47 request to a specific wallet.
     */
    suspend fun sendNwcRequestToWallet(
        walletUri: Nip47WalletConnect.Nip47URINorm?,
        request: Request,
        onResponse: (Response?) -> Unit,
    ): Pair<LnZapPaymentRequestEvent, NormalizedRelayUrl> {
        val walletService = walletUri ?: throw IllegalArgumentException("No NIP47 setup")
        val walletSigner = buildSigner(walletService) ?: signer

        val event = LnZapPaymentRequestEvent.createRequest(request, walletService.pubKeyHex, walletSigner, useNip44 = prefersNip44(walletService))

        val filter =
            NWCPaymentQueryState(
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                relay = walletService.relayUri,
            )

        val assembler = nwcFilterAssembler()

        // Synchronous flush so the REQ frame is queued on the WebSocket before
        // the EVENT is published. Without this, the bundler may delay REQ up
        // to 500ms, and the wallet service's ephemeral kind 23195 reply can
        // be missed.
        assembler.subscribeAndFlush(filter)

        // Safety net: drop the filter after 60s if the wallet never replies.
        // The happy path (response arrives) cancels this job and unsubscribes
        // through assembler.unsubscribeSoon, which debounces.
        val timeoutJob =
            scope.launch(Dispatchers.IO) {
                delay(60000)
                assembler.unsubscribe(filter)
            }

        val responseCache = NostrWalletConnectResponseCache(walletSigner)
        cache.consume(event, null, true, walletService.relayUri) {
            timeoutJob.cancel()
            onResponse(responseCache.decryptResponse(it))
            assembler.unsubscribeSoon(filter)
        }

        return Pair(event, walletService.relayUri)
    }

    /**
     * Sends a zap payment request to the default wallet.
     */
    suspend fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onResponse: (Response?) -> Unit,
    ): Pair<LnZapPaymentRequestEvent, NormalizedRelayUrl> {
        val walletService = defaultWalletUri.value ?: throw IllegalArgumentException("No NIP47 setup")

        val event = LnZapPaymentRequestEvent.create(bolt11, walletService.pubKeyHex, nip47Signer.value, useNip44 = prefersNip44(walletService))

        val filter =
            NWCPaymentQueryState(
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                relay = walletService.relayUri,
            )

        val assembler = nwcFilterAssembler()

        // Synchronous flush so the REQ frame is queued before the EVENT.
        // See sendNwcRequestToWallet above for the rationale.
        assembler.subscribeAndFlush(filter)

        // Safety net: drop the filter after 60s if the wallet never replies.
        // The happy path (response arrives) cancels this job and instead
        // hands off to assembler.unsubscribeSoon, which debounces.
        val timeoutJob =
            scope.launch(Dispatchers.IO) {
                delay(60000) // waits 1 minute to complete payment.
                assembler.unsubscribe(filter)
            }

        cache.consume(event, zappedNote, true, walletService.relayUri) {
            timeoutJob.cancel()
            onResponse(decryptResponse(it))
            assembler.unsubscribeSoon(filter)
        }

        return Pair(event, walletService.relayUri)
    }
}
