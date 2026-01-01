/**
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
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentQueryState
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.NostrWalletConnectRequestCache
import com.vitorpamplona.quartz.nip47WalletConnect.NostrWalletConnectResponseCache
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages NIP-47 (Nostr Wallet Connect) related signing operations and decryption cache for a given account.
 *
 * Key Responsibilities:
 *
 * - Dynamically creates a NIP-47 signer if the wallet setup changes in the account settings.
 * - Provides decryption caches to manage decrypted NIP-47 requests and responses efficiently.
 * - Handles creating of zap payment requests and waits for responses.
 *
 * @property signer the main Nostr signer used for general Nostr operations
 * @property cache the local cache for handling notes and events
 * @property scope the coroutine scope used for async operations
 * @property settings the account settings containing NIP-47 configuration
 */
class NwcSignerState(
    val signer: NostrSigner,
    val nwcFilterAssembler: NWCPaymentFilterAssembler,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) : INwcSignerState {
    /**
     * Derives a NIP-47 signer from the zap payment request in settings.
     * If there's no valid configuration, it defaults to the main signer.
     * Flows updates whenever settings change.
     */
    val nip47Signer =
        settings.zapPaymentRequest
            .map {
                buildSigner(it) ?: signer
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                buildSigner(settings.zapPaymentRequest.value) ?: signer,
            )

    /**
     * Creates a dedicated request decryption cache for the NIP-47 signer.
     * Flows updates whenever the signer changes.
     */
    val zapPaymentRequestDecryptionCache =
        nip47Signer
            .map {
                NostrWalletConnectRequestCache(it)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, NostrWalletConnectRequestCache(nip47Signer.value))

    /**
     * Creates a dedicated response decryption cache for the NIP-47 signer.
     * Flows updates whenever the signer changes.
     */
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

    fun hasWalletConnectSetup(): Boolean = settings.zapPaymentRequest.value != null

    override fun isNIP47Author(pubkeyHex: String?): Boolean = nip47Signer.value.pubKey == pubkeyHex

    /**
     * Decrypts a NIP-47 payment request using the current signer.
     *
     * @param nwcRequest the NIP-47 payment request event to decrypt
     * @return the decrypted request or null if not set up or decryption fails
     */
    override suspend fun decryptRequest(nwcRequest: LnZapPaymentRequestEvent): Request? {
        if (!hasWalletConnectSetup()) return null
        return zapPaymentRequestDecryptionCache.value.decryptRequest(nwcRequest)
    }

    /**
     * Decrypts a NIP-47 payment response using the current signer.
     *
     * @param nwsResponse the NIP-47 payment response event to decrypt
     * @return the decrypted response or null if not set up or decryption fails
     */
    override suspend fun decryptResponse(nwsResponse: LnZapPaymentResponseEvent): Response? {
        if (!hasWalletConnectSetup()) return null
        return zapPaymentResponseDecryptionCache.value.decryptResponse(nwsResponse)
    }

    /**
     * Sends a zap payment request to a connected Lightning wallet.
     * Subscribes to responses and waits up to 60s for a reply.
     *
     * @param bolt11 the BOLT-11 invoice to pay
     * @param zappedNote the note being zapped (if any)
     * @param onResponse callback to handle the response from the wallet
     * @return a pair containing the payment request event and target relay URL
     * @throws IllegalArgumentException if no NIP-47 wallet is set up
     */
    suspend fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onResponse: (Response?) -> Unit,
    ): Pair<LnZapPaymentRequestEvent, NormalizedRelayUrl> {
        val walletService = settings.zapPaymentRequest.value
        if (walletService == null) throw IllegalArgumentException("No NIP47 setup")

        val event = LnZapPaymentRequestEvent.create(bolt11, walletService.pubKeyHex, nip47Signer.value)

        val filter =
            NWCPaymentQueryState(
                fromServiceHex = walletService.pubKeyHex,
                toUserHex = event.pubKey,
                replyingToHex = event.id,
                relay = walletService.relayUri,
            )

        nwcFilterAssembler.subscribe(filter)

        scope.launch(Dispatchers.IO) {
            delay(60000) // waits 1 minute to complete payment.
            nwcFilterAssembler.unsubscribe(filter)
        }

        cache.consume(event, zappedNote, true, walletService.relayUri) {
            onResponse(decryptResponse(it))
        }

        return Pair(event, walletService.relayUri)
    }
}
