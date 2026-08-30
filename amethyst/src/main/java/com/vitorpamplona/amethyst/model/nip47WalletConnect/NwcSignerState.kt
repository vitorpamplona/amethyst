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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentQueryState
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
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
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcNotificationEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PaymentReceivedNotification
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
     * Shared cache of wallets' kind 13194 info events, used here to negotiate
     * encryption. Injected by [com.vitorpamplona.amethyst.model.Account] (which
     * owns the relay client). Null in tests / when unavailable — requests then
     * fall back to NIP-04.
     */
    val infoCache: NwcInfoCache? = null,
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

    init {
        // Warm the info cache in the background whenever the default wallet changes
        // so the payment hot path can read the encryption preference without waiting.
        scope.launch(Dispatchers.IO) {
            defaultWalletUri
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.pubKeyHex == b.pubKeyHex && a.relayUri == b.relayUri }
                .collect { infoCache?.refreshIfStale(it) }
        }
    }

    /**
     * The negotiated encryption preference for a wallet. NIP-47 says a client
     * "should always prefer nip44 if supported by the wallet service", so a false
     * here has to mean "the wallet does not offer NIP-44" — not "we have not asked
     * yet".
     *
     * That distinction is why this waits. The info cache is per-account and held in
     * memory only, so it starts empty on every app launch, and reading it without
     * waiting made the first transaction to each wallet after every launch fall
     * back to NIP-04 even against a wallet advertising `nip44_v2`. Only a cold
     * cache waits: a stale entry still says what the wallet advertises and is used
     * as-is while it refreshes in the background.
     *
     * The wait is capped, because this sits in front of a payment the user has
     * already tapped. Its own fetch is bounded only by the relay accessory's 30s
     * idle window, and the no-response timer below does not start until this
     * returns. On expiry we send NIP-04 for this one request rather than hold the
     * tap; the fetch keeps running in the cache's scope, so the next request gets
     * the negotiated scheme. Never bound the fetch itself instead — a null from it
     * is cached as a definitive "no info event" for the whole TTL, which would pin
     * the wallet to NIP-04 for days.
     */
    private suspend fun prefersNip44(uri: Nip47WalletConnect.Nip47URINorm?): Boolean {
        uri ?: return false
        val info = withTimeoutOrNull(NIP44_NEGOTIATION_WAIT_MS) { infoCache?.currentOrFetch(uri) }
        return info?.encryptionSchemes()?.any { it.equals("nip44_v2", ignoreCase = true) } == true
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

    // Non-zap incoming payments reported by connected wallets (NIP-47
    // payment_received). Buffered + drop-oldest so a burst never blocks the
    // decrypt coroutine; consumers (e.g. the tray-notification poster) collect it.
    private val _incomingNonZapPayments =
        MutableSharedFlow<NwcTransaction>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incomingNonZapPayments: SharedFlow<NwcTransaction> = _incomingNonZapPayments.asSharedFlow()

    /**
     * Decrypts an incoming NWC notification (kind 23197/23196) with the matching
     * wallet's connection secret and, when it is a non-zap `payment_received`,
     * publishes its transaction to [incomingNonZapPayments]. Zap-carrying payments
     * are dropped — those already surface via the kind-9735 ZapNotification path.
     */
    suspend fun handleIncomingNotification(event: NwcNotificationEvent) {
        if (!hasWalletConnectSetup()) return

        // The notification is `p`-tagged to the per-wallet client pubkey; match it
        // to the wallet whose connection secret derives that key.
        val clientPubKey = event.clientPubKey() ?: return
        val wallet = settings.nwcWallets.value.firstOrNull { buildSigner(it.uri)?.pubKey == clientPubKey } ?: return
        val walletSigner = buildSigner(wallet.uri) ?: return

        val notification =
            try {
                event.decryptNotification(walletSigner)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return
            }

        val tx = (notification as? PaymentReceivedNotification)?.notification ?: return
        if (tx.parsedMetadata()?.nostr != null) return // zap — already shown by ZapNotification

        _incomingNonZapPayments.tryEmit(tx)
    }

    /**
     * Sends a generic NIP-47 request to the default wallet.
     */
    suspend fun sendNwcRequest(
        request: Request,
        onTimeout: () -> Unit = {},
        onResponse: (Response?) -> Unit,
    ): Pair<LnZapPaymentRequestEvent, NormalizedRelayUrl> = sendNwcRequestToWallet(defaultWalletUri.value, request, onTimeout, onResponse)

    /**
     * Sends a generic NIP-47 request to a specific wallet.
     */
    suspend fun sendNwcRequestToWallet(
        walletUri: Nip47WalletConnect.Nip47URINorm?,
        request: Request,
        onTimeout: () -> Unit = {},
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

        val timeoutJob = launchGiveUpTimer(assembler, filter, event.id, onTimeout)

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
        onTimeout: () -> Unit = {},
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

        val timeoutJob = launchGiveUpTimer(assembler, filter, event.id, onTimeout)

        cache.consume(event, zappedNote, true, walletService.relayUri) {
            timeoutJob.cancel()
            onResponse(decryptResponse(it))
            assembler.unsubscribeSoon(filter)
        }

        return Pair(event, walletService.relayUri)
    }

    /**
     * Safety net for a wallet that never replies: drops the subscription filter and retires
     * the request. The happy path cancels this job and unsubscribes through
     * [NWCPaymentFilterAssembler.unsubscribeSoon] instead, which debounces.
     */
    private fun launchGiveUpTimer(
        assembler: NWCPaymentFilterAssembler,
        filter: NWCPaymentQueryState,
        requestId: HexKey,
        onTimeout: () -> Unit,
    ) = scope.launch(Dispatchers.IO) {
        delay(NWC_RESPONSE_TIMEOUT_MS)
        assembler.unsubscribe(filter)
        giveUpWaiting(requestId, onTimeout)
    }

    /**
     * Retires a request whose response never arrived: removes the tracker entry so it
     * does not leak, and tells the caller so the user hears about it. A silent give-up
     * is the worst outcome for a payment UI — the action just appears not to have
     * happened, which is indistinguishable from a refusal the wallet did send.
     *
     * A `cleanup` that returns false means a response beat us to the tracker entry,
     * so the response path is already reporting and this must stay quiet.
     */
    private fun giveUpWaiting(
        requestId: HexKey,
        onTimeout: () -> Unit,
    ) {
        val wasStillPending = cache.paymentTracker.cleanup(requestId)
        if (wasStillPending) {
            Log.w("NwcSignerState") {
                "No NIP-47 response for request $requestId after ${NWC_RESPONSE_TIMEOUT_MS}ms; giving up and dropping the subscription."
            }
            onTimeout()
        }
    }

    companion object {
        /**
         * How long a NIP-47 request waits for its kind-23195 reply before the client
         * gives up. Exposed in seconds so the UI can name the number it shows the user.
         */
        const val NWC_RESPONSE_TIMEOUT_SECONDS = 60

        const val NWC_RESPONSE_TIMEOUT_MS = NWC_RESPONSE_TIMEOUT_SECONDS * 1000L

        /**
         * How long a request will wait for a cold info cache before falling back to
         * NIP-04. Comfortably over a healthy single-relay round trip, far under the
         * 30s the fetch itself would otherwise allow in front of a payment tap.
         */
        const val NIP44_NEGOTIATION_WAIT_MS = 3_000L
    }
}
