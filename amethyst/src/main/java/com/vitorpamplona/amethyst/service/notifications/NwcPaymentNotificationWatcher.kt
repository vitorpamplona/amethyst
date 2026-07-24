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
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.notifications.renderers.NwcPaymentNotifier
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcNotificationEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PaymentReceivedNotification
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Always-on watcher that turns NIP-47 wallet notifications (kind 23197/23196) into
 * user-facing tray notifications for the logged-in account.
 *
 * For each configured NWC wallet it opens a standing subscription on that wallet's
 * own relay, filtered to notifications `p`-tagged to our client pubkey. Each event
 * is decrypted with the per-wallet connection secret and, when it is an incoming
 * `payment_received`, posted via [NwcPaymentNotifier] — **unless** the transaction
 * metadata carries a NIP-57 zap request, in which case it is dropped: zaps already
 * surface through the kind-9735 `ZapNotification` path, so notifying here would
 * double up.
 *
 * Only new notifications (`since` = watch start) are surfaced, so relaunching the
 * app never replays old payments as fresh alerts; a per-subscription id set
 * de-duplicates re-delivery across relay reconnects.
 */
class NwcPaymentNotificationWatcher(
    private val context: Context,
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val accountFlow: Flow<Account?>,
) {
    fun start() {
        scope.launch(Dispatchers.IO) {
            accountFlow
                .distinctUntilChanged { a, b -> a?.signer?.pubKey == b?.signer?.pubKey }
                .collectLatest { account ->
                    account ?: return@collectLatest
                    account.settings.nwcWallets.collectLatest { wallets ->
                        watchWallets(account, wallets)
                    }
                }
        }
    }

    private suspend fun watchWallets(
        account: Account,
        wallets: List<NwcWalletEntryNorm>,
    ) = coroutineScope {
        wallets.forEach { wallet ->
            val signer = account.nip47SignerState.buildSigner(wallet.uri) ?: return@forEach

            launch(Dispatchers.IO) {
                // Skip wallets that explicitly advertise no notification support, so we
                // don't hold open a relay connection that will never deliver. Fail open
                // when the info event is unknown (null) — better to listen than miss.
                val info = account.nwcInfoCache.getFresh(wallet.uri)
                if (info != null && !info.supportsNotifications()) return@launch

                val filter =
                    Filter(
                        kinds = listOf(NwcNotificationEvent.KIND, NwcNotificationEvent.LEGACY_KIND),
                        authors = listOf(wallet.uri.pubKeyHex),
                        tags = mapOf("p" to listOf(signer.pubKey)),
                        since = TimeUtils.now(),
                    )

                val seen = HashSet<HexKey>()
                client.subscribeAsFlow(wallet.uri.relayUri, filter).collect { events ->
                    events.forEach { event ->
                        val notification = event as? NwcNotificationEvent ?: return@forEach
                        if (seen.add(notification.id)) {
                            handle(account, notification, signer)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handle(
        account: Account,
        event: NwcNotificationEvent,
        signer: NostrSigner,
    ) {
        val notification =
            try {
                event.decryptNotification(signer)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return
            }

        val tx = (notification as? PaymentReceivedNotification)?.notification ?: return

        // A payment carrying a NIP-57 zap request is already shown by ZapNotification.
        if (tx.parsedMetadata()?.nostr != null) return

        NwcPaymentNotifier.notify(context, account, tx)
    }
}
