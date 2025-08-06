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

import android.util.Log
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.service.okhttp.ProxySettingsAnchor
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class RelayProxyClientConnector(
    val torProxySettingsAnchor: ProxySettingsAnchor,
    val okHttpClients: DualHttpClientManager,
    val connManager: ConnectivityManager,
    val client: NostrClient,
    val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    val relayServices =
        combine(
            torProxySettingsAnchor.flow,
            okHttpClients.defaultHttpClient,
            okHttpClients.defaultHttpClientWithoutProxy,
            connManager.status,
        ) { torSettings, torConnection, clearConnection, connectivity ->
            torSettings.hashCode() + torConnection.hashCode() + clearConnection.hashCode() + connectivity.hashCode()
        }.debounce(100)
            .onEach {
                Log.d("ManageRelayServices", "Relay Services have changed")
                client.reconnect(true)
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
