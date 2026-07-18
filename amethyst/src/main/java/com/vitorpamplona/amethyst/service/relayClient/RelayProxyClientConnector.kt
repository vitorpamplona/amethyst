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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.amethyst.model.torState.TorRelayEvaluation
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityStatus
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
import okhttp3.OkHttpClient

class RelayProxyClientConnector(
    val torEvaluator: StateFlow<TorRelayEvaluation>,
    val torConnection: StateFlow<OkHttpClient>,
    val clearConnection: StateFlow<OkHttpClient>,
    val connectivityStatus: StateFlow<ConnectivityStatus>,
    val torStatus: StateFlow<TorServiceStatus>,
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

    // The OkHttp clients in use the last time we forced a reconnect. These are only
    // rebuilt when something connection-relevant changes (Tor's SOCKS port appears,
    // wifi<->cellular switch), so comparing them tells us whether a wakeup is a real
    // transport change or just noise (Tor bootstrap status churn, connectivity blips).
    private var lastTorConnection: OkHttpClient? = null
    private var lastClearConnection: OkHttpClient? = null

    // The network we were last on. The OkHttp clients above are rebuilt off the metered
    // bit, so they only tell us about wifi<->cellular; they say nothing about wifi A ->
    // wifi B, a VPN coming up, or a captive portal clearing. Those all mint a new
    // networkHandle, and after them every existing socket is bound to an interface that
    // is gone and every accumulated backoff was earned against a network we have left.
    private var lastNetworkId: Long? = null

    // The user's Tor preferences. TorRelayEvaluation has no equals() and a fresh instance is
    // emitted on unrelated churn, so we track the settings by value. Without this, flipping a
    // Tor toggle while Tor is already up leaves both OkHttpClient references identical ->
    // transportChanged=false -> a relay parked on a 5-minute backoff keeps waiting it out on
    // a transport it is no longer using.
    //
    // Deliberately only [TorRelaySettings], not the trusted/DM/money relay sets that
    // TorRelayEvaluation also carries: those churn constantly while an account's relay lists
    // load from the network (observed firing three times during a single cold start). A relay
    // moving between classifications can flip its transport too, but forgiving the whole
    // pool's backoff every time any relay list updates is far more damage than making that
    // one relay serve out its delay.
    private var lastTorSettings: TorRelaySettings? = null

    @OptIn(FlowPreview::class)
    val relayServices =
        combine(
            torEvaluator,
            torConnection,
            clearConnection,
            connectivityStatus,
            torStatus,
        ) { torSettings, torConnection, clearConnection, connectivity, torStatus ->
            RelayServiceInfra(torSettings, torConnection, clearConnection, connectivity, torStatus)
        }.debounce(100)
            .onEach { apply(it) }
            .onStart {
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

    /**
     * Decides what a change in the relay infrastructure means for the pool. Split out of the
     * flow so the decision table can be exercised directly, without a debounce and a shared
     * StateFlow in the way.
     */
    fun apply(infra: RelayServiceInfra) {
        val networkId = (infra.connectivity as? ConnectivityStatus.Active)?.networkId
        val torSettings = infra.evaluator.torSettings

        when {
            infra.connectivity is ConnectivityStatus.StartingService -> {
                // ignore
            }

            infra.connectivity is ConnectivityStatus.Off -> {
                Log.d("ManageRelayServices") { "Connectivity Off: Pausing Relay Services ${infra.connectivity}" }
                if (client.isActive()) {
                    client.disconnect()
                }
                if (infra.torStatus is TorServiceStatus.Active) {
                    Log.d("ManageRelayServices", "Connectivity off, Tor idle")
                }
                // disconnect() already cleared every relay's backoff. Forget the network
                // so the next Active is treated as a fresh start rather than a change.
                lastNetworkId = null
            }

            infra.connectivity is ConnectivityStatus.Active && !client.isActive() -> {
                Log.d("ManageRelayServices", "Connectivity On: Resuming Relay Services")

                if (infra.torStatus is TorServiceStatus.Active) {
                    Log.d("ManageRelayServices", "Connectivity resumed, Tor active")
                }

                // only calls this if the client is not active. Otherwise goes to the else below
                client.connect()
                lastNetworkId = networkId
                lastTorSettings = torSettings
                lastTorConnection = infra.torConnection
                lastClearConnection = infra.clearConnection
            }

            else -> {
                // Only skip the per-relay exponential backoff when the actual HTTP
                // transport changed. Otherwise (e.g. Tor still bootstrapping, the SOCKS
                // port not yet listening) honor each relay's backoff so we don't
                // reconnect-fail-reconnect on every unrelated infrastructure event.
                val transportChanged =
                    infra.torConnection !== lastTorConnection || infra.clearConnection !== lastClearConnection

                // A different network entirely. Every socket is bound to an interface that
                // no longer carries traffic, and needsToReconnect() cannot see that (it only
                // compares the proxy and the timeouts), so those sockets would otherwise sit
                // there until OkHttp's 120s ping finally fails.
                val networkChanged = networkId != null && lastNetworkId != null && networkId != lastNetworkId

                // Same network and same OkHttp clients, but the user re-classified which
                // relays go through Tor. The relays whose transport flipped must re-dial now.
                val torPolicyChanged = lastTorSettings != null && torSettings != lastTorSettings

                val previousNetworkId = lastNetworkId

                lastTorConnection = infra.torConnection
                lastClearConnection = infra.clearConnection
                lastNetworkId = networkId ?: lastNetworkId
                lastTorSettings = torSettings

                if (networkChanged) {
                    Log.d("ManageRelayServices") {
                        "Network identity changed ($previousNetworkId -> $networkId), rebuilding every relay connection"
                    }
                    // Full teardown: disconnect() drops the dead sockets AND clears each
                    // relay's backoff, so the new network starts from a clean slate.
                    client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
                } else {
                    val freshStart = transportChanged || torPolicyChanged
                    if (freshStart) {
                        // The failures behind the current backoffs were measured against a
                        // transport we are no longer using. ignoreRetryDelays alone only
                        // skips the gate once and still doubles the stored delay, so a relay
                        // that fails this one dial would come back worse off than before.
                        client.resetBackoff()
                    }

                    Log.d("ManageRelayServices") {
                        "Relay Services have changed, reconnecting relays that need to " +
                            "(transportChanged=$transportChanged torPolicyChanged=$torPolicyChanged)"
                    }
                    client.reconnect(
                        onlyIfChanged = true,
                        ignoreRetryDelays = freshStart,
                    )
                }
            }
        }
    }
}
