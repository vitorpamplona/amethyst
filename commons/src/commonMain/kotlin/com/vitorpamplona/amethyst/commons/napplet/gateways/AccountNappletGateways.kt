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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayCleartext
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletWalletGateway
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * The napplet gateways an [Account] alone can back, shared by every host: publishing and querying
 * through the account's relays, and paying through its NWC wallet. Platform pieces (consent UI,
 * notifications, uploads) stay with each host.
 */
class AccountNappletRelayGateway(
    private val account: Account,
) : NappletRelayGateway {
    override suspend fun publish(event: Event): List<String> {
        val relays = account.computeRelayListToBroadcast(event)
        account.client.publish(event, relays)
        return relays.map { it.url }
    }

    /** Bounded live relay fetch (EOSE/timeout) for all [filters], merged with the local cache, newest-first. */
    override suspend fun query(filters: List<Filter>): List<Event> {
        if (filters.isEmpty()) return emptyList()
        val relays = account.homeRelays.flow.value
        val fromRelays =
            if (relays.isEmpty()) {
                emptyList()
            } else {
                runCatchingCancellable {
                    account.client.taggedAs(SubPurpose.ADD_ONS, "Napplet").fetchAll(filters = relays.associateWith { filters }, idleTimeoutMs = QUERY_TIMEOUT.inWholeMilliseconds)
                }.getOrDefault(emptyList())
            }
        val fromCache = filters.flatMap { filter -> account.cache.filter(filter).mapNotNull { it.event } }
        val merged =
            (fromRelays + fromCache)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
        val limit = filters.mapNotNull { it.limit }.maxOrNull()
        val limited = limit?.let { merged.take(it) } ?: merged
        return limited.mapNotNull { NappletRelayCleartext.forDelivery(it, account.signer) }
    }

    private companion object {
        val QUERY_TIMEOUT = 8.seconds
    }
}

/**
 * Pays an invoice through the account's connected NWC wallet, returning the preimage on success.
 * Throws (→ `Failed`) when no wallet is connected, the wallet reports an error, or it does not
 * respond in time — so the applet never silently believes a payment succeeded.
 */
class AccountNappletWalletGateway(
    private val account: Account,
) : NappletWalletGateway {
    override suspend fun payInvoice(invoice: String): String? {
        if (account.nip47SignerState.defaultWalletUri.value == null) {
            throw IllegalStateException("No Lightning wallet is connected.")
        }
        val result = CompletableDeferred<String?>()
        account.zaps.sendZapPaymentRequestFor(invoice, null) { response ->
            when (response) {
                is PayInvoiceSuccessResponse -> result.complete(response.result?.preimage)
                is PayInvoiceErrorResponse -> result.completeExceptionally(RuntimeException(response.error?.message ?: "Payment failed."))
                is NwcErrorResponse -> result.completeExceptionally(RuntimeException(response.error?.message ?: "Wallet error."))
                else -> result.completeExceptionally(RuntimeException("Unexpected wallet response."))
            }
        }
        return withTimeout(WALLET_TIMEOUT) { result.await() }
    }

    private companion object {
        val WALLET_TIMEOUT = 60.seconds
    }
}
