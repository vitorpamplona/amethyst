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
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
) : INwcSignerState {
    /**
     * Flow of the default wallet's NWC URI, derived from multi-wallet settings.
     */
    val defaultWalletUri: StateFlow<Nip47WalletConnect.Nip47URINorm?> =
        combine(settings.nwcWallets, settings.defaultNwcWalletId) { wallets, defaultId ->
            if (defaultId != null) {
                wallets.firstOrNull { it.id == defaultId }?.uri
            } else {
                wallets.firstOrNull()?.uri
            }
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

        val event = LnZapPaymentRequestEvent.createRequest(request, walletService.pubKeyHex, walletSigner)

        val filter =
            NWCPaymentQueryState(
                fromServiceHex = walletService.pubKeyHex,
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                relay = walletService.relayUri,
            )

        val assembler = nwcFilterAssembler()

        assembler.subscribe(filter)

        scope.launch(Dispatchers.IO) {
            delay(60000)
            assembler.unsubscribe(filter)
        }

        val responseCache = NostrWalletConnectResponseCache(walletSigner)
        cache.consume(event, null, true, walletService.relayUri) {
            onResponse(responseCache.decryptResponse(it))
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

        val event = LnZapPaymentRequestEvent.create(bolt11, walletService.pubKeyHex, nip47Signer.value)

        val filter =
            NWCPaymentQueryState(
                fromServiceHex = walletService.pubKeyHex,
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                relay = walletService.relayUri,
            )

        val assembler = nwcFilterAssembler()

        assembler.subscribe(filter)

        scope.launch(Dispatchers.IO) {
            delay(60000) // waits 1 minute to complete payment.
            assembler.unsubscribe(filter)
        }

        cache.consume(event, zappedNote, true, walletService.relayUri) {
            onResponse(decryptResponse(it))
        }

        return Pair(event, walletService.relayUri)
    }
}
