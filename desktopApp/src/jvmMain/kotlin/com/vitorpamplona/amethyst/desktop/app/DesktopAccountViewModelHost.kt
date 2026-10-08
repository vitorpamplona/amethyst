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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlTransport
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobFailure
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModelHost
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.ConcurrentHashMap

/** What every account's ViewModel reaches on a desktop: the shared graph in [modules]. */
class DesktopAccountViewModelHost(
    private val modules: DesktopAppModules,
) : AccountViewModelHost {
    // The JVM has no memory-pressure callback: this is the heap watchdog's.
    override val memoryPressure: Flow<Unit> get() = modules.memoryPressure

    // The local Blossom cache (BUD-10 bridge on 127.0.0.1) is not probed on desktop yet.
    override val localBlossomCacheAvailable: Flow<Boolean> get() = flowOf(false)

    override val powPublishFailures: Flow<PoWJobFailure> get() = modules.powPublishQueue.failures

    override val relayStats: RelayStats get() = modules.relayStats

    override val websocketBuilder: WebsocketBuilder get() = modules.websocketBuilder

    override val lnurlTransport: LnurlHttpTransport by lazy { OkHttpLnurlTransport(modules.roleBasedHttpClientBuilder::okHttpClientForMoney) }

    override val moneyOpRelays: MoneyOpRelayRouting get() = modules.torEvaluatorFlow

    override val torRelayEvaluation: StateFlow<TorRelayEvaluation> get() = modules.torEvaluatorFlow.flow

    override val relayAuthState: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>
        get() = modules.authCoordinator.receiver.authStateFlow

    override val mediaUploader: MediaUploader by lazy { DesktopMediaUploader(modules::blossomClient, modules.roleBasedHttpClientBuilder) }

    override val scheduledPostStore: ScheduledPostStore get() = modules.scheduledPostStore

    // One flow per login, so the backup nudge watching it hears the change and goes away.
    private val backedUpKeys = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    override suspend fun hasBackedUpKeys(npub: String): StateFlow<Boolean> = backedUpKeys[npub] ?: MutableStateFlow(modules.sessionStore.hasBackedUpKeys(npub)).let { backedUpKeys.putIfAbsent(npub, it) ?: it }

    override suspend fun setHasBackedUpKeys(
        npub: String,
        value: Boolean,
    ) {
        modules.sessionStore.setHasBackedUpKeys(value, npub)
        backedUpKeys[npub]?.value = value
    }

    override val savedAccounts: Flow<Set<HexKey>> =
        modules.sessionStore
            .accountsFlow()
            .map { toPubKeys(it) }
            .onStart { emit(toPubKeys(modules.sessionStore.allSavedAccounts())) }

    // Not wired to the desktop notifier yet: a toast for a message read here stays until dismissed.
    override fun dismissNotificationFor(eventId: HexKey) = Unit

    override fun openLightningWallet(invoice: String): Boolean = DesktopBrowser.open("lightning:$invoice")

    private fun toPubKeys(accounts: List<AccountInfo>?): Set<HexKey> =
        accounts
            ?.mapNotNull {
                try {
                    it.npub.bechToBytes().toHexKey()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            }?.toSet() ?: emptySet()
}
