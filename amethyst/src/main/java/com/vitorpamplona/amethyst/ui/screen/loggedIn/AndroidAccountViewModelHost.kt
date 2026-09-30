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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.app.NotificationManager
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.AppModules
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.audio.AnonymizedResult
import com.vitorpamplona.amethyst.commons.audio.VoicePreset
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.ai.WritingAssistant
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlTransport
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobFailure
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModelHost
import com.vitorpamplona.amethyst.service.ai.WritingAssistantFactory
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.dismissNotificationForEvent
import com.vitorpamplona.amethyst.service.uploads.AndroidMediaUploader
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceAnonymizer
import com.vitorpamplona.amethyst.ui.note.payViaIntent
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import okio.Path

/** [AccountViewModelHost] over the app's [AppModules]. Lazy services are read on first use. */
class AndroidAccountViewModelHost(
    private val modules: AppModules,
) : AccountViewModelHost {
    override val memoryPressure: Flow<Unit> get() = modules.memoryPressureEvents

    override val localBlossomCacheAvailable: Flow<Boolean> get() = modules.localBlossomCacheProbe.available

    override val powPublishFailures: Flow<PoWJobFailure> get() = modules.powPublishQueue.failures

    override val relayStats: RelayStats get() = modules.relayStats

    override val websocketBuilder: WebsocketBuilder get() = modules.websocketBuilder

    override val lnurlTransport: LnurlHttpTransport by lazy { OkHttpLnurlTransport(modules.roleBasedHttpClientBuilder::okHttpClientForMoney) }

    override val moneyOpRelays: MoneyOpRelayRouting get() = modules.torEvaluatorFlow

    override val torRelayEvaluation: StateFlow<TorRelayEvaluation> get() = modules.torEvaluatorFlow.flow

    override val relayAuthState: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>
        get() = modules.authCoordinator.receiver.authStateFlow

    override val mediaUploader: MediaUploader by lazy { AndroidMediaUploader(modules.appContext) }

    override val deviceLocation: DeviceLocation get() = modules.locationManager

    override val scheduledPostStore: ScheduledPostStore get() = modules.scheduledPostStore

    override suspend fun anonymizeVoice(
        input: Path,
        presetName: String,
    ): Result<AnonymizedResult> = VoiceAnonymizer().anonymize(input.toFile(), VoicePreset.valueOf(presetName))

    // Built on the application context: the assistant outlives any one Activity inside the ViewModel.
    override fun createWritingAssistant(): WritingAssistant = WritingAssistantFactory.create(modules.appContext)

    override val savedAccounts: Flow<Set<HexKey>> =
        LocalPreferences
            .accountsFlow()
            .map { toPubKeys(it) }
            .onStart { emit(toPubKeys(LocalPreferences.allSavedAccounts())) }

    override fun dismissNotificationFor(eventId: HexKey) {
        ContextCompat
            .getSystemService(modules.appContext, NotificationManager::class.java)
            ?.dismissNotificationForEvent(eventId)
    }

    override fun openLightningWallet(invoice: String): Boolean {
        var opened = true
        payViaIntent(invoice, modules.appContext, "", onPaid = {}, onError = { opened = false })
        return opened
    }

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
