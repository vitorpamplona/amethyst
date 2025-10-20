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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.amethyst.model.torState.TorRelayEvaluation
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.freehaven.tor.control.TorControlCommands
import okhttp3.OkHttpClient

class RelayProxyClientConnector(
    val torEvaluator: StateFlow<TorRelayEvaluation>,
    val okHttpClients: DualHttpClientManager,
    val connManager: ConnectivityManager,
    val torManager: TorManager,
    val client: INostrClient,
    val scope: CoroutineScope,
) {
    data class RelayServiceInfra(
        val evaluator: TorRelayEvaluation,
        val torConnection: OkHttpClient,
        val clearConnection: OkHttpClient,
        val connectivity: ConnectivityStatus,
        val torStatus: TorServiceStatus,
    )

    @OptIn(FlowPreview::class)
    val relayServices =
        combine(
            torEvaluator,
            okHttpClients.defaultHttpClient,
            okHttpClients.defaultHttpClientWithoutProxy,
            connManager.status,
            torManager.status,
        ) { torSettings, torConnection, clearConnection, connectivity, torStatus ->
            RelayServiceInfra(torSettings, torConnection, clearConnection, connectivity, torStatus)
        }.debounce(100)
            .onEach {
                if (it.connectivity is ConnectivityStatus.StartingService) {
                    // ignore
                } else if (it.connectivity is ConnectivityStatus.Off) {
                    Log.d("ManageRelayServices", "Connectivity Off: Pausing Relay Services ${it.connectivity}")
                    if (client.isActive()) {
                        client.disconnect()
                    }
                    if (it.torStatus is TorServiceStatus.Active) {
                        it.torStatus.torControlConnection?.signal(TorControlCommands.SIGNAL_DORMANT)
                        Log.d("ManageRelayServices", "Pausing Tor Activity")
                    }
                } else if (it.connectivity is ConnectivityStatus.Active && !client.isActive()) {
                    Log.d("ManageRelayServices", "Connectivity On: Resuming Relay Services")

                    if (it.torStatus is TorServiceStatus.Active) {
                        it.torStatus.torControlConnection?.signal(TorControlCommands.SIGNAL_ACTIVE)
                        it.torStatus.torControlConnection?.signal(TorControlCommands.SIGNAL_NEWNYM)
                        Log.d("ManageRelayServices", "Resuming Tor Activity with new nym")
                    }

                    // only calls this if the client is not active. Otherwise goes to the else below
                    client.connect()
                } else {
                    Log.d("ManageRelayServices", "Relay Services have changed, reconnecting relays that need to")
                    client.reconnect(
                        onlyIfChanged = true,
                        ignoreRetryDelays = true,
                    )
                }
            }.onStart {
                Log.d("ManageRelayServices", "Resuming Relay Services")
                client.connect()
            }.onCompletion {
                Log.d("ManageRelayServices", "Pausing Relay Services")
                client.disconnect()
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(30000),
                null,
            )
}
